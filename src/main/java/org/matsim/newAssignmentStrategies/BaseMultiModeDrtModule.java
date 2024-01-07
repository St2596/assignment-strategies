package org.matsim.newAssignmentStrategies;


import com.google.inject.Inject;
import org.matsim.contrib.drt.analysis.DrtModeAnalysisModule;
import org.matsim.contrib.drt.routing.MultiModeDrtMainModeIdentifier;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtModeModule;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.router.MainModeIdentifier;

public final class BaseMultiModeDrtModule extends AbstractModule {
    @Inject
    private MultiModeDrtConfigGroup multiModeDrtCfg;

    double maxEuclideanDistance;

    @Override
    public void install() {
        for (DrtConfigGroup drtCfg : multiModeDrtCfg.getModalElements()) {
            install(new DrtModeModule(drtCfg));
            installQSimModule(new BaseDrtModeQSimModule(drtCfg, maxEuclideanDistance));
            install(new DrtModeAnalysisModule(drtCfg));
        }

        bind(MainModeIdentifier.class).toInstance(new MultiModeDrtMainModeIdentifier(multiModeDrtCfg));
    }
}
