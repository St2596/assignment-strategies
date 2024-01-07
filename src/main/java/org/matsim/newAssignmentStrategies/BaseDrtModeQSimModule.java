package org.matsim.newAssignmentStrategies;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.*;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.depot.NearestStartLinkAsDepot;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.ParallelPathDataProvider;
import org.matsim.contrib.drt.optimizer.insertion.PrecalculablePathDataProvider;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.passenger.DrtRequestCreator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.schedule.DrtTaskFactoryImpl;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.DrtScheduleTimingUpdater;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.drt.scheduler.RequestInsertionScheduler;
import org.matsim.contrib.drt.vrpagent.DrtActionCreator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.passenger.DefaultPassengerRequestValidator;
import org.matsim.contrib.dvrp.passenger.PassengerEngine;
import org.matsim.contrib.dvrp.passenger.PassengerEngineQSimModule;
import org.matsim.contrib.dvrp.passenger.PassengerRequestCreator;
import org.matsim.contrib.dvrp.passenger.PassengerRequestValidator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentSourceQSimModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class BaseDrtModeQSimModule extends AbstractDvrpModeQSimModule {
    private final DrtConfigGroup drtCfg;
    private final double catchmentRadius;

    public BaseDrtModeQSimModule(DrtConfigGroup drtCfg, double catchmentRadius) {
        super(drtCfg.getMode());
        this.drtCfg = drtCfg;
        this.catchmentRadius = catchmentRadius;

    }

    @Override
    protected void configureQSim() {
        install(new VrpAgentSourceQSimModule(getMode()));
        install(new PassengerEngineQSimModule(getMode()));

        addModalComponent(DrtOptimizer.class, modalProvider(
                getter -> new BaseDrtOptimizer(drtCfg, getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
                        getter.getModal(DepotFinder.class), getter.getModal(RebalancingStrategy.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(DrtScheduleTimingUpdater.class),
                        getter.getModal(EmptyVehicleRelocator.class),
                        getter.getModal(UnplannedRequestInserter.class))));

        bindModal(DepotFinder.class).toProvider(
                modalProvider(getter -> new NearestStartLinkAsDepot(getter.getModal(Fleet.class))));

        bindModal(PassengerRequestValidator.class).to(DefaultPassengerRequestValidator.class).asEagerSingleton();



        addModalComponent(QSimScopeForkJoinPoolHolder.class,
                () -> new QSimScopeForkJoinPoolHolder(drtCfg.getNumberOfThreads()));

        bindModal(UnplannedRequestInserter.class)
                .toProvider(modalProvider(getter -> new BaseUnplannedRequestInserter(drtCfg,
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

        bindModal(VehicleData.EntryFactory.class).toInstance(new VehicleDataEntryFactoryImpl(drtCfg));

        bindModal(InsertionCostCalculator.PenaltyCalculator.class).to(
                drtCfg.isRejectRequestIfMaxWaitOrTravelTimeViolated() ?
                        InsertionCostCalculator.RejectSoftConstraintViolations.class :
                        InsertionCostCalculator.DiscourageSoftConstraintViolations.class).asEagerSingleton();

        bindModal(DrtTaskFactory.class).toInstance(new DrtTaskFactoryImpl());

        bindModal(EmptyVehicleRelocator.class).toProvider(
                new ModalProviders.AbstractProvider<EmptyVehicleRelocator>(drtCfg.getMode()) {

                    @Inject
                    @Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
                    private TravelTime travelTime;

                    @Inject
                    private MobsimTimer timer;

                    @Override
                    public EmptyVehicleRelocator get() {
                        Network network = getModalInstance(Network.class);
                        DrtTaskFactory taskFactory = getModalInstance(DrtTaskFactory.class);
                        TravelDisutility travelDisutility = getModalInstance(
                                TravelDisutilityFactory.class).createTravelDisutility(travelTime);
                        return new EmptyVehicleRelocator(network, travelTime, travelDisutility, timer, taskFactory);
                    }
                }).asEagerSingleton();

        bindModal(DrtScheduleInquiry.class).to(DrtScheduleInquiry.class).asEagerSingleton();

        bindModal(RequestInsertionScheduler.class).toProvider(modalProvider(
                getter -> new RequestInsertionScheduler(drtCfg, getter.getModal(Fleet.class),
                        getter.get(MobsimTimer.class),
                        getter.getNamed(TravelTime.class, DvrpTravelTimeModule.DVRP_ESTIMATED),
                        getter.getModal(DrtScheduleTimingUpdater.class), getter.getModal(DrtTaskFactory.class))))
                .asEagerSingleton();

        bindModal(DrtScheduleTimingUpdater.class).toProvider(new Provider<DrtScheduleTimingUpdater>() {
            @Inject
            private MobsimTimer timer;

            @Override
            public DrtScheduleTimingUpdater get() {
                return new DrtScheduleTimingUpdater(drtCfg, timer);
            }
        }).asEagerSingleton();

        addModalComponent(ParallelPathDataProvider.class,
                new ModalProviders.AbstractProvider<ParallelPathDataProvider>(getMode()) {
                    @Inject
                    @Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
                    private TravelTime travelTime;

                    @Override
                    public ParallelPathDataProvider get() {
                        Network network = getModalInstance(Network.class);
                        TravelDisutility travelDisutility = getModalInstance(
                                TravelDisutilityFactory.class).createTravelDisutility(travelTime);
                        return new ParallelPathDataProvider(network, travelTime, travelDisutility, drtCfg);
                    }
                });
        bindModal(PrecalculablePathDataProvider.class).to(modalKey(ParallelPathDataProvider.class));

        bindModal(VrpAgentLogic.DynActionCreator.class).
                toProvider(modalProvider(getter -> new DrtActionCreator(getter.getModal(PassengerEngine.class),
                        getter.get(MobsimTimer.class), getter.get(DvrpConfigGroup.class)))).
                asEagerSingleton();

        bindModal(PassengerRequestCreator.class).toProvider(new Provider<DrtRequestCreator>() {
            @Inject
            private EventsManager events;
            @Inject
            private MobsimTimer timer;

            @Override
            public DrtRequestCreator get() {
                return new DrtRequestCreator(getMode(), events, timer);
            }
        }).asEagerSingleton();

        bindModal(VrpOptimizer.class).to(modalKey(DrtOptimizer.class));
    }
}