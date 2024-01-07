package org.matsim.outputAnalysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class DrtPersonStuckEventHandler implements PersonStuckEventHandler {
    private Set<Id<Person>> personIds;
    private int iteration = 0;
    private final int binSize;
    private final int nofBins;
    private final Map<String, DataFrame> data = new TreeMap<>();
    public DrtPersonStuckEventHandler(Set<Id<Person>> personIds, int binSize, int nofBins) {
        this.personIds = personIds;
        this.binSize = binSize;
        this.nofBins = nofBins;
    }

    @Override
    public void handleEvent(PersonStuckEvent event) {
        int index = getBinIndex(event.getTime());
        if ((this.personIds == null || this.personIds.contains(event.getPersonId())) && event.getLegMode() != null){
            DataFrame dataFrame = getDataForMode(event.getLegMode());
            dataFrame.countsStuck[index]++;
        }
    }

    private int getBinIndex(final double time) {
        int bin = (int)(time / this.binSize);
        if (bin >= this.nofBins) {
            return this.nofBins;
        }
        return bin;
    }

    DataFrame getDataForMode(final String legMode) {
        DataFrame dataFrame = this.data.get(legMode);
        if (dataFrame == null) {
            dataFrame = new DataFrame(this.binSize, this.nofBins + 1); // +1 for all times out of our range
            this.data.put(legMode, dataFrame);
        }
        return dataFrame;
    }

    DataFrame getAllModesData() {
        DataFrame result = new DataFrame(this.binSize, this.nofBins + 1);
        for (DataFrame byMode : this.data.values()) {
            for (int i=0;i<result.countsStuck.length;++i) {
                result.countsStuck[i] += byMode.countsStuck[i];
            }
        }
        return result;
    }
    static class DataFrame {
        final int[] countsDep;
        final int[] countsArr;
        final int[] countsStuck;
        final int binSize;
        public DataFrame(final int binSize, final int nofBins) {
            this.countsDep = new int[nofBins];
            this.countsArr = new int[nofBins];
            this.countsStuck = new int[nofBins];
            this.binSize = binSize;
        }
    }

}
