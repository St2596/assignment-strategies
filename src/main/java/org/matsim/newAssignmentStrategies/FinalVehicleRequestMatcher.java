package org.matsim.newAssignmentStrategies;


import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtDriveTask;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.core.router.FastAStarEuclideanFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

public class FinalVehicleRequestMatcher {
    private final LeastCostPathCalculator leastCostPathCalculator;
    private final TravelTime travelTime;
    private final DrtTaskFactory taskFactory;
    private final double stopDuration;

    public FinalVehicleRequestMatcher(TravelTime travelTime, DrtTaskFactory taskFactory, DrtConfigGroup drtCfg,
                                      Network network, TravelDisutility travelDisutility) {
        this.travelTime = travelTime;
        this.taskFactory = taskFactory;
        stopDuration = drtCfg.getStopDuration();
        leastCostPathCalculator = new FastAStarEuclideanFactory().createPathCalculator(network, travelDisutility,
                travelTime);
    }

    public void assignIdlingVehicleToRequest(DvrpVehicle vehicle, DrtRequest request, double timeOfTheDay) {
        Schedule schedule = vehicle.getSchedule();

        // Update the end time of the final stay task
        updateFinalStayTaskEndTime(schedule, timeOfTheDay);

        // Create and append a drive task if necessary
        double scheduledPickUpTime = appendDriveTaskIfNeeded(vehicle, schedule, request, timeOfTheDay);

        // Append Stop task for pick-up
        DrtStopTask pickUpStopTask = appendStopTask(vehicle, schedule, scheduledPickUpTime, request, true);

        // Create and append another drive task for transporting the user
        double scheduledArrivalTime = appendDriveTaskForCustomerIfNeeded(vehicle, schedule, request, pickUpStopTask);

        // Append Stop task for drop off
        appendStopTask(vehicle, schedule, scheduledArrivalTime, request, false);

        // Append a new final stay task
        appendFinalStayTask(vehicle, schedule);
    }

    private void updateFinalStayTaskEndTime(Schedule schedule, double timeOfTheDay) {
        DrtStayTask finalStayTask = (DrtStayTask) schedule.getTasks().get(schedule.getTaskCount() - 1);
        finalStayTask.setEndTime(timeOfTheDay);
    }

    private double appendDriveTaskIfNeeded(DvrpVehicle vehicle, Schedule schedule, DrtRequest request, double startTime) {
        DrtStayTask finalStayTask = (DrtStayTask) schedule.getTasks().get(schedule.getTaskCount() - 1);
        if (finalStayTask.getLink() != request.getFromLink()) {
            VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(finalStayTask.getLink(), request.getFromLink(),
                    startTime, leastCostPathCalculator, travelTime);
            DrtDriveTask driveTask = taskFactory.createDriveTask(vehicle, path);
            schedule.addTask(driveTask);
            return driveTask.getEndTime();
        }
        return startTime;
    }

    private DrtStopTask appendStopTask(DvrpVehicle vehicle, Schedule schedule, double startTime, DrtRequest request, boolean isPickup) {
        Link taskLink = isPickup ? request.getFromLink() : request.getToLink();
        double endTime = Math.max(startTime + stopDuration, isPickup ? request.getEarliestStartTime() : startTime + stopDuration);
        DrtStopTask stopTask = taskFactory.createStopTask(vehicle, startTime, endTime, taskLink);
        schedule.addTask(stopTask);
        if (isPickup) {
            stopTask.addPickupRequest(request);
            request.setPickupTask(stopTask);
        } else {
            stopTask.addDropoffRequest(request);
            request.setDropoffTask(stopTask);
        }
        return stopTask;
    }

    private double appendDriveTaskForCustomerIfNeeded(DvrpVehicle vehicle, Schedule schedule, DrtRequest request, DrtStopTask previousStopTask) {
        if (request.getFromLink() != request.getToLink()) {
            VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(request.getFromLink(), request.getToLink(),
                    previousStopTask.getEndTime(), leastCostPathCalculator, travelTime);
            DrtDriveTask driveTask = taskFactory.createDriveTask(vehicle, path);
            schedule.addTask(driveTask);
            return driveTask.getEndTime();
        }
        return previousStopTask.getEndTime();
    }

    private void appendFinalStayTask(DvrpVehicle vehicle, Schedule schedule) {
        DrtStayTask lastTask = (DrtStayTask) schedule.getTasks().get(schedule.getTaskCount() - 1);
        DrtStayTask newFinalStayTask = taskFactory.createStayTask(vehicle, lastTask.getEndTime(),
                vehicle.getServiceEndTime(), lastTask.getLink());
        schedule.addTask(newFinalStayTask);
    }

}
