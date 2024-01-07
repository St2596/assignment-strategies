package org.matsim.run;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.av.robotaxi.fares.drt.DrtFareModule;
import org.matsim.contrib.av.robotaxi.fares.drt.DrtFaresConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.newAssignmentStrategies.BaseStrategyModule;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

public class RunPTDrtExample {
    private static Logger LOG = Logger.getLogger(RunPTDrtExample.class);

    public static void main(String[] args) {
        Config config = ConfigUtils.loadConfig("input.xml", new MultiModeDrtConfigGroup(),
                new DvrpConfigGroup(),
                new OTFVisConfigGroup(), new DrtFaresConfigGroup());
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.plansCalcRoute().setInsertingAccessEgressWalk(true);
        config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
        config.controler().setRoutingAlgorithmType( ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);

        SwissRailRaptorConfigGroup configGroup = new SwissRailRaptorConfigGroup();
        configGroup.setUseIntermodalAccessEgress(true);
        configGroup.setIntermodalAccessEgressModeSelection(SwissRailRaptorConfigGroup.IntermodalAccessEgressModeSelection.RandomSelectOneModePerRoutingRequestAndDirection);

        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet parameterSetDrt = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        parameterSetDrt.setMode(TransportMode.drt);
        parameterSetDrt.setInitialSearchRadius(1000);
        parameterSetDrt.setMaxRadius(20000);
        parameterSetDrt.setSearchExtensionRadius(10);
        configGroup.addIntermodalAccessEgress(parameterSetDrt);


        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSetWalk = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        paramSetWalk.setMode(TransportMode.walk);
        paramSetWalk.setInitialSearchRadius(50);
        paramSetWalk.setMaxRadius(1000);
        paramSetWalk.setSearchExtensionRadius(0.1);
        configGroup.addIntermodalAccessEgress(paramSetWalk);


        config.addModule(configGroup);
        config.qsim().setEndTime(108000);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = DrtControlerCreator.createControlerWithSingleModeDrt(config, false);
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)));
        controler.addOverridingModule(new DrtFareModule());
        controler.addOverridingModule(new SwissRailRaptorModule());

        MultiModeDrtConfigGroup multiModeDrtCfg = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
		for(DrtConfigGroup drtcfg : multiModeDrtCfg.getModalElements()){
			DrtConfigs.adjustDrtConfig(drtcfg, config.planCalcScore(), config.plansCalcRoute());
			double catchmentRadius= 2000;
            controler.addOverridingQSimModule(new BaseStrategyModule(drtcfg, catchmentRadius));
		}

        controler.run();
    }
}
