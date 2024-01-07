package org.matsim.newAssignmentStrategies;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.depot.Depots;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.optimizer.rebalancing.mincostflow.MinCostFlowRebalancingParams;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.DrtScheduleTimingUpdater;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequests;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

public class BaseDrtOptimizer implements DrtOptimizer {
    private static final Logger log = Logger.getLogger(BaseDrtOptimizer.class);

    private final DrtConfigGroup drtCfg;
    private final Integer rebalancingInterval;
    private final Fleet fleet;
    private final DrtScheduleInquiry scheduleInquiry;
    private final DrtScheduleTimingUpdater scheduleTimingUpdater;
    private final RebalancingStrategy rebalancingStrategy;
    private final MobsimTimer mobsimTimer;
    private final DepotFinder depotFinder;
    private final EmptyVehicleRelocator relocator;
    private final UnplannedRequestInserter requestInserter;
    double dispatchingInterval;

    private final Collection<DrtRequest> unplannedRequests = new TreeSet<DrtRequest>(
            PassengerRequests.ABSOLUTE_COMPARATOR);
    private boolean requiresReoptimization = false;

    public BaseDrtOptimizer(DrtConfigGroup drtCfg, Fleet fleet, MobsimTimer mobsimTimer, DepotFinder depotFinder,
                            RebalancingStrategy rebalancingStrategy, DrtScheduleInquiry scheduleInquiry,
                            DrtScheduleTimingUpdater scheduleTimingUpdater, EmptyVehicleRelocator relocator,
                            UnplannedRequestInserter requestInserter) {
        this.drtCfg = drtCfg;
        this.fleet = fleet;
        this.mobsimTimer = mobsimTimer;
        this.depotFinder = depotFinder;
        this.rebalancingStrategy = rebalancingStrategy;
        this.scheduleInquiry = scheduleInquiry;
        this.scheduleTimingUpdater = scheduleTimingUpdater;
        this.relocator = relocator;
        this.requestInserter = requestInserter;
        rebalancingInterval = drtCfg.getMinCostFlowRebalancing()
                .map(MinCostFlowRebalancingParams::getInterval)
                .orElse(null);
        dispatchingInterval = 60;
    }



    @Override
    public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e) {
        if (requiresReoptimization) {
            for (DvrpVehicle v : fleet.getVehicles().values()) {
                scheduleTimingUpdater.updateTimings(v);
            }
            if (e.getSimulationTime() % dispatchingInterval == 0){
                requestInserter.scheduleUnplannedRequests(unplannedRequests);
            }
            requestInserter.scheduleUnplannedRequests(unplannedRequests);
            requiresReoptimization = false;
        }

//        if (rebalancingInterval != null && e.getSimulationTime() % rebalancingInterval == 0) {
//            rebalanceFleet();
//        }
    }

    private void rebalanceFleet() {
        // right now we relocate only idle vehicles (vehicles that are being relocated cannot be relocated)
        Stream<? extends DvrpVehicle> rebalancableVehicles = fleet.getVehicles()
                .values()
                .stream()
                .filter(scheduleInquiry::isIdle);
        List<RebalancingStrategy.Relocation> relocations = rebalancingStrategy.calcRelocations(rebalancableVehicles,
                mobsimTimer.getTimeOfDay());

        if (!relocations.isEmpty()) {
            log.debug("Fleet rebalancing: #relocations=" + relocations.size());
            for (RebalancingStrategy.Relocation r : relocations) {
                Link currentLink = ((DrtStayTask)r.vehicle.getSchedule().getCurrentTask()).getLink();
                if (currentLink != r.link) {
                    relocator.relocateVehicle(r.vehicle, r.link);
                }
            }
        }
    }

    @Override
    public void requestSubmitted(Request request) {
        unplannedRequests.add((DrtRequest)request);
        requiresReoptimization = true;
    }

    @Override
    public void nextTask(DvrpVehicle vehicle) {
        scheduleTimingUpdater.updateBeforeNextTask(vehicle);

        vehicle.getSchedule().nextTask();

        // if STOP->STAY then choose the best depot
        if (drtCfg.getIdleVehiclesReturnToDepots() && Depots.isSwitchingFromStopToStay(vehicle)) {
            Link depotLink = depotFinder.findDepot(vehicle);
            if (depotLink != null) {
                relocator.relocateVehicle(vehicle, depotLink);
            }
        }
    }
}

