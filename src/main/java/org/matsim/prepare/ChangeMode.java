package org.matsim.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;


public class ChangeMode {


    public static void main(String[] args) {
        int counter = 0;
        Config config;

        config = ConfigUtils.loadConfig("input.xml");
        Scenario scenario = ScenarioUtils.loadScenario(config);

        for (Id<Person> personId : scenario.getPopulation().getPersons().keySet()) {
            counter++;
            System.out.println(personId);
            Person eachPerson = scenario.getPopulation().getPersons().get(personId);
            System.out.println(eachPerson);
            Plan eachPlan = eachPerson.getSelectedPlan();
            System.out.println(eachPlan);
            int NoOfPlans = eachPlan.getPlanElements().size();
            System.out.println(NoOfPlans);
            for (int j = 1; j < NoOfPlans; j += 2) {
                Leg legToCheck = (Leg) eachPlan.getPlanElements().get(j);
                String legToCheckMode = legToCheck.getMode().toString().trim();
                if (legToCheckMode.equals("ride"))  legToCheck.setMode("car");

            }
        }
        System.out.println(counter);

        PopulationWriter populationWriter = new PopulationWriter(scenario.getPopulation());
        populationWriter.write("output.xml");
        System.out.println("writing done");


    }
}
