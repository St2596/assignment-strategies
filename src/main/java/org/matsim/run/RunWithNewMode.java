package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

public class RunWithNewMode {

    public static void main( String[] args ){

        Config config = ConfigUtils.loadConfig( "input.xml");
        config.controler().setOverwriteFileSetting( OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists );
        config.controler().setLastIteration( 100 );

        {
            StrategyConfigGroup.StrategySettings stratSets = new StrategyConfigGroup.StrategySettings();
            stratSets.setStrategyName( DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode );
            stratSets.setWeight( 0.15 );
            config.strategy().addStrategySettings( stratSets );

            config.changeMode().setModes( new String [] {"car", "Vehicle Passenger", "TNC", "pt"});
        }

        {
            StrategyConfigGroup.StrategySettings stratSets = new StrategyConfigGroup.StrategySettings();
            stratSets.setStrategyName( DefaultPlanStrategiesModule.DefaultStrategy.ReRoute );
            stratSets.setWeight( 0.1 );
            config.strategy().addStrategySettings( stratSets );

        }
//
        {
            config.plansCalcRoute().setNetworkModes(CollectionUtils.stringToSet("car, TNC"));
        }

        {
            config.qsim().setMainModes( CollectionUtils.stringToSet( "car, TNC" ) );
            config.qsim().setVehiclesSource( QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData );
            config.qsim().setLinkDynamics( QSimConfigGroup.LinkDynamics.PassingQ );
            config.qsim().setNumberOfThreads(4);
            config.global().setNumberOfThreads(4);
        }

        {
            PlanCalcScoreConfigGroup.ModeParams params = new PlanCalcScoreConfigGroup.ModeParams("TNC");
            params.setConstant(2.);
            config.planCalcScore().addModeParams(params);
        }

        Scenario scenario = ScenarioUtils.loadScenario( config );

        for( Link link : scenario.getNetwork().getLinks().values() ){
            link.setAllowedModes( CollectionUtils.stringToSet( "car, TNC" ) );
        }

        VehiclesFactory vf = scenario.getVehicles().getFactory();
        {
            VehicleType vehicleType = vf.createVehicleType( Id.create( "TNC", VehicleType.class ));
            vehicleType.setMaximumVelocity( 60./3.6 );
            vehicleType.setNetworkMode("TNC");
            scenario.getVehicles().addVehicleType( vehicleType );
        }

        {
            VehicleType vehicleType = vf.createVehicleType( Id.create( "car", VehicleType.class ));
            scenario.getVehicles().addVehicleType( vehicleType );
        }

        Controler controler = new Controler( scenario );
        controler.run();
    }


}

