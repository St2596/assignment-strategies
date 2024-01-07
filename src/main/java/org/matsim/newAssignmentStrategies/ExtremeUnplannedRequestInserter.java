package org.matsim.newAssignmentStrategies;


import ch.sbb.matsim.routing.pt.raptor.*;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.Facility;

import java.util.*;
import java.util.stream.Collectors;


public class ExtremeUnplannedRequestInserter implements UnplannedRequestInserter {
    private static final Logger log = Logger.getLogger(ExtremeUnplannedRequestInserter.class);
    private static final String NO_SUITABLE_VEHICLE_FOUND_CAUSE = "no_suitable_vehicle_found";
    private static final String BETTER_PUBLIC_TRANSPORT_OPTION_AVAILABLE = "a_better_public_transport_option_is_available";
    private final double maxCatchmentRadius = 100000;

    private final DrtConfigGroup drtCfg;
    private final Fleet fleet;
    private final EventsManager eventsManager;
    private final MobsimTimer mobsimTimer;
    private final double catchmentRadius;
    private final double maxWaitTime = 300;
    private final DrtScheduleInquiry scheduleInquiry;
    private final FinalVehicleRequestMatcher finalVehicleRequestMatcher;

    private Map<Id<Person>, ? extends Person> persons;
    private SwissRailRaptorData raptorData;
    private RaptorRouteSelector raptorRouteSelector;
    private RaptorStopFinder raptorStopFinder;
    private RaptorParametersForPerson personParametersForRaptorDefault;

    public ExtremeUnplannedRequestInserter(DrtConfigGroup drtCfg, Fleet fleet, EventsManager eventsManager,
                                           MobsimTimer mobsimTimer, DrtScheduleInquiry drtScheduleInquiry,
                                           FinalVehicleRequestMatcher finalVehicleRequestMatcher, double catchmentRadius) {
        this.drtCfg = drtCfg;
        this.fleet = fleet;
        this.eventsManager = eventsManager;
        this.mobsimTimer = mobsimTimer;
        this.scheduleInquiry = drtScheduleInquiry;
        this.finalVehicleRequestMatcher = finalVehicleRequestMatcher;
        this.catchmentRadius = catchmentRadius;
        initializePTComponents();
    }

    private void initializePTComponents() {
        Config config = ConfigUtils.loadConfig("input.xml");
        Scenario scenario = ScenarioUtils.loadScenario(config);

        this.persons = scenario.getPopulation().getPersons();
        this.raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), new RaptorStaticConfig(), scenario.getNetwork());
        this.raptorRouteSelector = new ConfigurableRaptorRouteSelector();
        this.raptorStopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), config,
                new DefaultRaptorIntermodalAccessEgress(), new HashMap<>());
        this.personParametersForRaptorDefault = new DefaultRaptorParametersForPerson(config);
    }

    @Override
    public void scheduleUnplannedRequests(Collection<DrtRequest> unplannedRequests) {
        if (unplannedRequests.isEmpty()) {
            return;
        }
        double timeOfDay = mobsimTimer.getTimeOfDay();
        List<? extends DvrpVehicle> idleVehicles = fleet.getVehicles().values().stream().filter(scheduleInquiry::isIdle)
                .collect(Collectors.toList());

        Iterator<DrtRequest> reqIter = unplannedRequests.iterator();

        while (reqIter.hasNext() && !idleVehicles.isEmpty()) {
            DrtRequest request = reqIter.next();
            handleRequest(request, timeOfDay, idleVehicles, reqIter);
        }
    }
    private void handleRequest(DrtRequest request, double timeOfDay, List<? extends DvrpVehicle> idleVehicles, Iterator<DrtRequest> reqIter) {
            Link requestLink = request.getFromLink();
            Link destinationLink = request.getToLink();

            List<DvrpVehicle> feasibleVehicles = new ArrayList<>();
            for (DvrpVehicle vehicle : idleVehicles) {
                Link vehicleLink = ((DrtStayTask) vehicle.getSchedule().getCurrentTask()).getLink();
                double euclideanDistance = NetworkUtils.getEuclideanDistance(requestLink.getCoord(),
                        vehicleLink.getCoord());
                if (euclideanDistance <= catchmentRadius) {
                    feasibleVehicles.add(vehicle);
                }
            }
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
        Person person = persons.get(Id.create(request.getPassengerId(), Person.class));

        Facility originFacility = new LinkWrapperFacility(requestLink);
        Facility destinationFacility = new LinkWrapperFacility(destinationLink);

        SwissRailRaptor swissRailRaptor = new SwissRailRaptor(raptorData, personParametersForRaptorDefault, raptorRouteSelector, raptorStopFinder);
        List<RaptorRoute> routes = swissRailRaptor.calcRoutes(originFacility, destinationFacility, request.getEarliestStartTime(), request.getLatestStartTime(), request.getLatestArrivalTime(), person);
        RaptorRoute route = new LeastCostRaptorRouteSelector().selectOne(routes, request.getLatestStartTime());
        double timeByPT = route.getTravelTime();
        double timeByDRT = NetworkUtils.getEuclideanDistance(requestLink.getCoord(), destinationLink.getCoord()) / 8.3334;

                double alpha = 1.4;
                double beta = 120;

                if (timeByPT <= alpha * timeByDRT + beta) {
                    eventsManager.processEvent(new PassengerRequestRejectedEvent(timeOfDay, drtCfg.getMode(),
                            request.getId(), request.getPassengerId(), BETTER_PUBLIC_TRANSPORT_OPTION_AVAILABLE));
                    log.debug("the destination is far from public transport stops " + request + " from passenger id="
                            + request.getPassengerId() + " fromLinkId=" + request.getFromLink().getId()
                            + ". Therefore the request is rejected!");
                    reqIter.remove();
                } else {
                    if (selectedVehicle != null) {
                        // Assign the vehicle to the request
                        finalVehicleRequestMatcher.assignIdlingVehicleToRequest(selectedVehicle, request, timeOfDay);
                        // Notify MATSim that a request is scheduled
                        eventsManager.processEvent(new PassengerRequestScheduledEvent(timeOfDay, drtCfg.getMode(),
                                request.getId(), request.getPassengerId(), selectedVehicle.getId(),
                                request.getPickupTask().getEndTime(), request.getDropoffTask().getBeginTime()));

                        // Remove the request and the vehicle from their respective collections
                        idleVehicles.remove(selectedVehicle);
                        reqIter.remove();
                    } else if (timeOfDay > request.getSubmissionTime() + maxWaitTime || timeOfDay >= 108000) {
                        eventsManager.processEvent(new PassengerRequestRejectedEvent(timeOfDay, drtCfg.getMode(),
                                request.getId(), request.getPassengerId(), NO_SUITABLE_VEHICLE_FOUND_CAUSE));
                        log.debug("No suitable vehicle found for drt request " + request + " from passenger id="
                                + request.getPassengerId() + " fromLinkId=" + request.getFromLink().getId()
                                + ". Therefore the request is rejected!");
                        reqIter.remove();
                    }
                }
            }
        }



