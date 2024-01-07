package org.matsim.outputAnalysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.util.HashMap;
import java.util.Map;

public class AverageTravelTimeCalculator implements PersonDepartureEventHandler,	PersonArrivalEventHandler {
    private final	Map<Id<Person>,	Double>	departureTimes	=	new	HashMap<>();
    private double	travelTimeSum	=	0.0;
    private int	travelTimeCount	=	0;
    @Override
    public void handleEvent(PersonDepartureEvent event) {
        this.departureTimes.put(event.getPersonId(),	event.getTime());
    }
    @Override
    public void handleEvent(PersonArrivalEvent	event) {
        double	departureTime	=	this.departureTimes.get(event.getPersonId());
        double	travelTime	=	event.getTime()	-	departureTime;
        this.travelTimeSum	+=	travelTime;
        this.travelTimeCount++;
    }
    public double getAverageTravelTime() {
        return this.travelTimeSum/this.travelTimeCount;
    }
}