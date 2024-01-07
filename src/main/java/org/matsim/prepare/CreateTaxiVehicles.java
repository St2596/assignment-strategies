package org.matsim.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class CreateTaxiVehicles {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        int numberofVehicles = 500;
        double operationStartTime = 0.; //t0
        double operationEndTime =36*3600.;	//t1
        int seats = 4;
        String networkfile = "imput.xml";
        String taxisFile = "taxis_4_"+numberofVehicles+".xml";
        List<DvrpVehicleSpecification> vehicles = new ArrayList<>();
        Random random = MatsimRandom.getLocalInstance();
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkfile);
        List<Id<Link>> allLinks = new ArrayList<>();
        allLinks.addAll(scenario.getNetwork().getLinks().keySet());
        for (int i = 0; i< numberofVehicles;i++){
            Link startLink;
            do {
                Id<Link> linkId = allLinks.get(random.nextInt(allLinks.size()));
                startLink =  scenario.getNetwork().getLinks().get(linkId);
            }
            while (!startLink.getAllowedModes().contains(TransportMode.car));
            //for multi-modal networks: Only links where cars can ride should be used.
            vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder().id(Id.create("taxi" + i, DvrpVehicle.class))
                    .startLinkId(startLink.getId())
                    .capacity(seats)
                    .serviceBeginTime(operationStartTime)
                    .serviceEndTime(operationEndTime)
                    .build());

        }
        new FleetWriter(vehicles.stream()).write(taxisFile);
    }
}

