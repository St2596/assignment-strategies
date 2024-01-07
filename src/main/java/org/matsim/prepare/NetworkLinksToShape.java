package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.utils.gis.matsim2esri.network.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.ArrayList;
import java.util.Collection;

public class NetworkLinksToShape {

        private static Logger log = Logger.getLogger(NetworkLinksToShape.class);
        private final FeatureGenerator featureGenerator;
        private final Network network;
        private final String filename;

        public NetworkLinksToShape(Network network, String filename, String coordinateSystem) {
            this(network, filename, (FeatureGeneratorBuilder)(new FeatureGeneratorBuilderImpl(network, coordinateSystem)));
        }

        public NetworkLinksToShape(Network network, String filename, FeatureGeneratorBuilder builder) {
            this.network = network;
            this.filename = filename;
            this.featureGenerator = builder.createFeatureGenerator();
        }

        public void write() {
            log.info("creating features...");
            Collection<SimpleFeature> features = new ArrayList();
            Link[] var2 = NetworkUtils.getSortedLinks(this.network);
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                Link link = var2[var4];
                features.add(this.featureGenerator.getFeature(link));
            }

            log.info("writing features to shape file... " + this.filename);
            ShapeFileWriter.writeGeometries(features, this.filename);
            log.info("done writing shape file.");
        }

        public static void main(String[] args) {
            String netfile = null;
            String outputFileLs = null;
            String outputFileP = null;
            String defaultCRS = "DHDN_GK4";
            boolean commonWealth = false;
            if (args.length == 0) {
                netfile = "input.xml.gz";
                outputFileLs = "networkLs.shp";
                outputFileP = "networkP.shp";
            } else if (args.length == 3) {
                netfile = args[0];
                outputFileLs = args[1];
                outputFileP = args[2];
            } else if (args.length == 4) {
                netfile = args[0];
                outputFileLs = args[1];
                outputFileP = args[2];
                defaultCRS = args[3];
            } else if (args.length == 5) {
                netfile = args[0];
                outputFileLs = args[1];
                outputFileP = args[2];
                defaultCRS = args[3];
                commonWealth = Boolean.parseBoolean(args[4]);
            } else {
                log.error("Arguments cannot be interpreted.  Aborting ...");
                System.exit(-1);
            }

            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            scenario.getConfig().global().setCoordinateSystem(defaultCRS);
            log.info("loading network from " + netfile);
            Network network = scenario.getNetwork();
            (new MatsimNetworkReader(scenario.getNetwork())).readFile(netfile);
            log.info("done.");
            FeatureGeneratorBuilderImpl builder = new FeatureGeneratorBuilderImpl(network, defaultCRS);
            builder.setFeatureGeneratorPrototype(LineStringBasedFeatureGenerator.class);
            builder.setWidthCoefficient(0.5D);
            builder.setWidthCalculatorPrototype(LanesBasedWidthCalculator.class);
            (new org.matsim.utils.gis.matsim2esri.network.Links2ESRIShape(network, outputFileLs, builder)).write();
            CoordinateReferenceSystem crs = MGC.getCRS(defaultCRS);
            builder.setWidthCoefficient((double)(commonWealth ? -1 : 1) * 0.003D);
            builder.setFeatureGeneratorPrototype(PolygonFeatureGenerator.class);
            builder.setWidthCalculatorPrototype(CapacityBasedWidthCalculator.class);
            builder.setCoordinateReferenceSystem(crs);
            (new NetworkLinksToShape(network, outputFileP, builder)).write();
        }
    }

