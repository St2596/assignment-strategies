package org.matsim.newAssignmentStrategies;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;


public class BaseStrategyModule extends AbstractDvrpModeQSimModule {

    private final DrtConfigGroup drtCfg;
    private final double catchmentRadius;

    public BaseStrategyModule(DrtConfigGroup drtCfg, double catchmentRadius) {
        super(drtCfg.getMode());
        this.drtCfg = drtCfg;
        this.catchmentRadius = catchmentRadius;
    }

    @Override
    protected void configureQSim() {

        bindModal(UnplannedRequestInserter.class)
                .toProvider(modalProvider(getter -> new BaseUnplannedRequestInserter( drtCfg,
                        getter.getModal(Fleet.class), getter.get(EventsManager.class), getter.get(MobsimTimer.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(FinalVehicleRequestMatcher.class),
                        catchmentRadius)))
                .asEagerSingleton();


        bindModal(FinalVehicleRequestMatcher.class).toProvider(new ModalProviders.AbstractProvider<FinalVehicleRequestMatcher>(drtCfg.getMode()) {
            @Inject
            @Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
            private TravelTime travelTime;

            @Override
            public FinalVehicleRequestMatcher get() {
                DrtTaskFactory taskFactory = getModalInstance(DrtTaskFactory.class);
                Network network = getModalInstance(Network.class);
                TravelDisutility travelDisutility = getModalInstance(TravelDisutilityFactory.class)
                        .createTravelDisutility(travelTime);
                return new FinalVehicleRequestMatcher(travelTime, taskFactory, drtCfg, network, travelDisutility);
            }
        }).asEagerSingleton();

    }
}