package org.matsim.newAssignmentStrategies;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.network.NetworkUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class BaseUnplannedRequestInserter implements UnplannedRequestInserter {
    private static final Logger log = Logger.getLogger(BaseUnplannedRequestInserter.class);
    private final DrtConfigGroup drtCfg;
    private final Fleet fleet;
    private final EventsManager eventsManager;
    private final MobsimTimer mobsimTimer;
    private final double catchmentRadius;
    private final double maxWaitTime = 300;
    private final DrtScheduleInquiry scheduleInquiry;
    private final FinalVehicleRequestMatcher finalVehicleRequestMatcher;
    private final double maxCatchmentRadius = 10000;
    private static final String NO_SUITABLE_VEHICLE_FOUND_CAUSE = "no_suitable_vehicle_found";
    public BaseUnplannedRequestInserter(DrtConfigGroup drtCfg, Fleet fleet, EventsManager eventsManager,
                                        MobsimTimer mobsimTimer, DrtScheduleInquiry drtScheduleInquiry,
                                        FinalVehicleRequestMatcher finalVehicleRequestMatcher, double catchmentRadius) {
        this.drtCfg = drtCfg;
        this.fleet = fleet;
        this.eventsManager = eventsManager;
        this.mobsimTimer = mobsimTimer;
        this.scheduleInquiry = drtScheduleInquiry;
        this.finalVehicleRequestMatcher = finalVehicleRequestMatcher;
        this.catchmentRadius = catchmentRadius;

    }

    @Override
    public void scheduleUnplannedRequests(Collection<DrtRequest> unplannedRequests) {
        if (unplannedRequests.isEmpty()) {
            return;
        }

        double timeOfDay = mobsimTimer.getTimeOfDay();
        List<? extends DvrpVehicle> idleVehicles = fleet.getVehicles().values().stream().filter(scheduleInquiry::isIdle)
                .collect(Collectors.toList());

        Iterator<DrtRequest> requestIterator = unplannedRequests.iterator();


        while (requestIterator.hasNext() && !idleVehicles.isEmpty()) {
            DrtRequest request = requestIterator.next();
            Link requestLink = request.getFromLink();

            List<DvrpVehicle> feasibleVehicles = new ArrayList<>();
            for (DvrpVehicle vehicle : idleVehicles) {
                Link vehicleLink = ((DrtStayTask) vehicle.getSchedule().getCurrentTask()).getLink();
                double distance = NetworkUtils.getEuclideanDistance(requestLink.getCoord(),
                        vehicleLink.getCoord());
                if (distance <= catchmentRadius) {
                    feasibleVehicles.add(vehicle);
                }
            }

            // Find the closest vehicles from the feasible vehicle list
            double shortestDistance = maxCatchmentRadius;
            DvrpVehicle selectedVehicle = null;
            for (DvrpVehicle vehicle : feasibleVehicles) {
                Link vehicleLink = ((DrtStayTask) vehicle.getSchedule().getCurrentTask()).getLink();
                double euclideanDistance = NetworkUtils.getEuclideanDistance(requestLink.getCoord(),
                        vehicleLink.getCoord());
                if (euclideanDistance < shortestDistance) {
                    shortestDistance = euclideanDistance;
                    selectedVehicle = vehicle;
                }
            }

            if (selectedVehicle != null) {
                // Assign the vehicle to the request
                finalVehicleRequestMatcher.assignIdlingVehicleToRequest(selectedVehicle, request, timeOfDay);
                // Notify MATSim that a request is scheduled
                eventsManager.processEvent(new PassengerRequestScheduledEvent(timeOfDay, drtCfg.getMode(),
                        request.getId(), request.getPassengerId(), selectedVehicle.getId(),
                        request.getPickupTask().getEndTime(), request.getDropoffTask().getBeginTime()));

                // Remove the request and the vehicle from their respective collections
                idleVehicles.remove(selectedVehicle);
                requestIterator.remove();
            } else if (timeOfDay > request.getSubmissionTime() + maxWaitTime || timeOfDay >= 108000) {
                eventsManager.processEvent(new PassengerRequestRejectedEvent(timeOfDay, drtCfg.getMode(),
                        request.getId(), request.getPassengerId(), NO_SUITABLE_VEHICLE_FOUND_CAUSE));
                log.debug("No suitable vehicle found for drt request " + request + " from passenger id="
                        + request.getPassengerId() + " fromLinkId=" + request.getFromLink().getId()
                        + ". Therefore the request is rejected!");
                requestIterator.remove();
            }
        }
    }
}



