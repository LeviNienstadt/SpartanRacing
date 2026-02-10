// Made for Siemens STAR-CCM+ 20.04.007-R8 by Levi Nienstadt
// If help is needed, contact via Slack, XXXXXXXXXX@gmail.com, or (XXX) XXX-XXXX

// To make changes, all changeable elements are at the top of the class below

package macro;

import java.util.*;
import java.io.*;
import java.lang.Math.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;
import star.vis.*;
import star.meshing.*;

class Sim {
    public Simulation simulation;

    public Sim(String simPath) {
        try { this.simulation = new Simulation(simPath); } catch(Exception e) {}
    }

    public void runSim() {
        simulation.getSimulationIterator().run();
    }

    public void executeMeshing() {
        AutoMeshOperation autoMeshOp = (AutoMeshOperation) simulation.get(MeshOperationManager.class).getObject("Automated Mesh");

        autoMeshOp.execute();
    }

    public void repairMesh() { // attempt to remove invalid cells from subtract region
        simulation.getMeshManager().removeInvalidCells(new ArrayList<>(Arrays.<star.common.Region>asList(simulation.getRegionManager().getRegion("Subtract"))), NeoProperty.fromString("{\'minimumContiguousFaceArea\': 0.0, \'minimumCellVolumeEnabled\': true, \'minimumVolumeChangeEnabled\': true, \'functionOperator\': 0, \'minimumContiguousFaceAreaEnabled\': true, \'minimumFaceValidityEnabled\': true, \'functionValue\': 0.0, \'functionEnabled\': false, \'function\': \'\', \'minimumVolumeChange\': 1.0E-10, \'minimumCellVolume\': 0.0, \'minimumCellQualityEnabled\': true, \'minimumCellQuality\': 1.0E-8, \'minimumDiscontiguousCells\': 1, \'minimumDiscontiguousCellsEnabled\': true, \'minimumFaceValidity\': 0.51}"));
    }

    public void setStopCriteria(double maxVel, double maxIterations) { // max vel if theres a meshing error, otherwise go to set iters
        MonitorIterationStoppingCriterion maxVelStopper = simulation.getSolverStoppingCriterionManager().createIterationStoppingCriterion(simulation.getMonitorManager().getMonitor("1 Maximum velocity Monitor"));
        MonitorIterationStoppingCriterion maxIterationsStopper = simulation.getSolverStoppingCriterionManager().createIterationStoppingCriterion(simulation.getMonitorManager().getMonitor("Iteration"));

        ((MonitorIterationStoppingCriterionOption) maxVelStopper.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.MAXIMUM);
        ((MonitorIterationStoppingCriterionOption) maxIterationsStopper.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.MAXIMUM);

        ((MonitorIterationStoppingCriterionMaxLimitType) maxVelStopper.getCriterionType()).getLimit().setValueAndUnits(maxVel, simulation.getUnitsManager().getObject("mph"));
        ((MonitorIterationStoppingCriterionMaxLimitType) maxIterationsStopper.getCriterionType()).getLimit().setValueAndUnits(maxIterations, simulation.getUnitsManager().getObject(""));
    }

    public void setReports() { // should be done in setup but redundant to make sure
        star.common.Region subtract = simulation.getRegionManager().getRegion("Subtract");
        Collection<Boundary> boundaries = subtract.getBoundaryManager().getBoundaries();

        ArrayList<Boundary> carFaces = new ArrayList<Boundary>();
        ArrayList<Boundary> FW = new ArrayList<Boundary>();
        ArrayList<Boundary> RW = new ArrayList<Boundary>();
        ArrayList<Boundary> UT = new ArrayList<Boundary>();

        Boundary FWMain = null;
        Boundary FWFlaps = null;

        Boundary RWMain = null;
        Boundary RWBiplane = null;
        Boundary RWFlaps = null;

        Boundary UTMain = null;
        Boundary UTTrailing = null;

        Boundary Canard = null;
        Boundary Diffuser = null;

        for (Boundary boundary : boundaries) { // inefficient but consistent
            String boundaryName = boundary.getPresentationName();
            if (!boundaryName.contains("Wind Tunnel")) {
                carFaces.add(boundary);

                if (boundaryName.contains("FW")) {
                    FW.add(boundary);
                }

                if (boundaryName.contains("RW")) {
                    RW.add(boundary);
                }

                if (boundaryName.contains("UT")) {
                    UT.add(boundary);
                }

                if (boundaryName.contains("FW Main")) { FWMain = boundary; }
                if (boundaryName.contains("FW Flaps")) { FWFlaps = boundary; }

                if (boundaryName.contains("RW Main")) { RWMain = boundary; }
                if (boundaryName.contains("RW Biplane")) { RWBiplane = boundary; }
                if (boundaryName.contains("RW Flaps")) { RWFlaps = boundary; }

                if (boundaryName.contains("UT Main")) { UTMain = boundary; }
                if (boundaryName.contains("UT Trailing Foils")) { UTTrailing = boundary; }

                if (boundaryName.contains("CANARD")) { Canard = boundary; }
                if (boundaryName.contains("DIFFUSER")) { Diffuser = boundary; }
            }
        }

        ForceReport drag = (ForceReport) simulation.getReportManager().getReport("1 Drag total");
        ForceReport df = (ForceReport) simulation.getReportManager().getReport("1 Downforce total");
        ForceReport dfFW = (ForceReport) simulation.getReportManager().getReport("DF FW");
        ForceReport dfFWMain = (ForceReport) simulation.getReportManager().getReport("DF FW Main");
        ForceReport dfFWFlaps = (ForceReport) simulation.getReportManager().getReport("DF FW Flaps");
        ForceReport dfRW = (ForceReport) simulation.getReportManager().getReport("DF RW");
        ForceReport dfRWMain = (ForceReport) simulation.getReportManager().getReport("DF RW Main");
        ForceReport dfRWBiplane = (ForceReport) simulation.getReportManager().getReport("DF RW Biplane");
        ForceReport dfRWFlaps = (ForceReport) simulation.getReportManager().getReport("DF RW Flaps");
        ForceReport dfUT = (ForceReport) simulation.getReportManager().getReport("DF UT");
        ForceReport dfUTMain = (ForceReport) simulation.getReportManager().getReport("DF UT Main");
        ForceReport dfUTTrailing = (ForceReport) simulation.getReportManager().getReport("DF UT Trailing Foils");
        ForceReport dfCanard = (ForceReport) simulation.getReportManager().getReport("DF CANARD");
        ForceReport dfDiffuser = (ForceReport) simulation.getReportManager().getReport("DF DIFFUSER");
        CenterOfLoadsReport clz = (CenterOfLoadsReport) simulation.getReportManager().getReport("Center of Loads z");
        CenterOfLoadsReport clx = (CenterOfLoadsReport) simulation.getReportManager().getReport("Center of Loads x");
        FrontalAreaReport fa = (FrontalAreaReport) simulation.getReportManager().getReport("Frontal Area");
        ForceCoefficientReport cl = (ForceCoefficientReport) simulation.getReportManager().getReport("Cl");
        ForceCoefficientReport cd = (ForceCoefficientReport) simulation.getReportManager().getReport("Cd");
        MaxReport maxVel = (MaxReport) simulation.getReportManager().getReport("1 Maximum velocity");

        drag.getParts().setQuery(null);
        df.getParts().setQuery(null);
        dfFW.getParts().setQuery(null);
        dfFWMain.getParts().setQuery(null);
        dfFWFlaps.getParts().setQuery(null);
        dfRW.getParts().setQuery(null);
        dfRWMain.getParts().setQuery(null);
        dfRWBiplane.getParts().setQuery(null);
        dfRWFlaps.getParts().setQuery(null);
        dfUT.getParts().setQuery(null);
        dfUTMain.getParts().setQuery(null);
        dfUTTrailing.getParts().setQuery(null);
        dfCanard.getParts().setQuery(null);
        dfDiffuser.getParts().setQuery(null);
        clz.getParts().setQuery(null);
        clx.getParts().setQuery(null);
        fa.getParts().setQuery(null);
        cl.getParts().setQuery(null);
        cd.getParts().setQuery(null);
        maxVel.getParts().setQuery(null);

        drag.getParts().setObjects(carFaces);
        df.getParts().setObjects(carFaces);
        dfFW.getParts().setObjects(FW);
        dfFWMain.getParts().setObjects(FWMain);
        dfFWFlaps.getParts().setObjects(FWFlaps);
        dfRW.getParts().setObjects(RW);
        dfRWMain.getParts().setObjects(RWMain);
        dfRWBiplane.getParts().setObjects(RWBiplane);
        dfRWFlaps.getParts().setObjects(RWFlaps);
        dfUT.getParts().setObjects(UT);
        dfUTMain.getParts().setObjects(UTMain);
        dfUTTrailing.getParts().setObjects(UTTrailing);
        dfCanard.getParts().setObjects(Canard);
        dfDiffuser.getParts().setObjects(Diffuser);
        clz.getParts().setObjects(carFaces);
        clx.getParts().setObjects(carFaces);
        fa.getParts().setObjects(carFaces);
        cl.getParts().setObjects(carFaces);
        cd.getParts().setObjects(carFaces);
        maxVel.getParts().setObjects(subtract);
    }

    public void save() {
        simulation.saveState(simulation.getSessionPath());
    }

    public void kill() {
        simulation.kill();
    }

    public void appendReports(String reportPath) { // reports are done as a CSV with new line for each new sim, makes it easy to copy paste into google sheets or excel
        boolean rideHeightSims = true; // sets custom report structure for ride height sims, set to either true or false

        Double df = round(Math.abs(simulation.getReportManager().getReport("1 Downforce total").getReportMonitorValue()), 2);
        Double drag = round(Math.abs(simulation.getReportManager().getReport("1 Drag total").getReportMonitorValue()), 2);
        Double conl = round(Math.abs(simulation.getReportManager().getReport("CoNL % front").getReportMonitorValue()), 2);

        Double FW = round(Math.abs(simulation.getReportManager().getReport("DF FW").getReportMonitorValue()), 2);
        Double FWMain = round(Math.abs(simulation.getReportManager().getReport("DF FW Main").getReportMonitorValue()), 2);
        Double FWFlaps = round(Math.abs(simulation.getReportManager().getReport("DF FW Flaps").getReportMonitorValue()), 2);
        
        Double RW = round(Math.abs(simulation.getReportManager().getReport("DF RW").getReportMonitorValue()), 2);
        Double RWMain = round(Math.abs(simulation.getReportManager().getReport("DF RW Main").getReportMonitorValue()), 2);
        Double RWFlaps = round(Math.abs(simulation.getReportManager().getReport("DF RW Flaps").getReportMonitorValue()), 2);
        Double RWBiplane = round(Math.abs(simulation.getReportManager().getReport("DF RW Biplane").getReportMonitorValue()), 2);
        
        Double UT = round(Math.abs(simulation.getReportManager().getReport("DF UT").getReportMonitorValue()), 2);
        Double UTMain = round(Math.abs(simulation.getReportManager().getReport("DF UT Main").getReportMonitorValue()), 2);
        Double UTTrailing = round(Math.abs(simulation.getReportManager().getReport("DF UT Trailing Foils").getReportMonitorValue()), 2);

        Double canard = round(Math.abs(simulation.getReportManager().getReport("DF CANARD").getReportMonitorValue()), 2);
        Double diffuser = round(Math.abs(simulation.getReportManager().getReport("DF DIFFUSER").getReportMonitorValue()), 2);
        Double cl = round(Math.abs(simulation.getReportManager().getReport("Cl").getReportMonitorValue()), 2);
        Double cd = round(Math.abs(simulation.getReportManager().getReport("Cd").getReportMonitorValue()), 2);
        Double fa = Math.abs(simulation.getReportManager().getReport("Frontal Area").getReportMonitorValue());

        String path = simulation.getSessionPath();
        String name = path.substring(path.lastIndexOf(path.contains("/") ? "/" : "\\") + 1, path.length()-4);
        String line = name + ",";

        if (rideHeightSims) {
            double FRH = Double.parseDouble(name.substring(name.indexOf("FRH") + 4, name.indexOf("RRH") - 1));
            double RRH = Double.parseDouble(name.substring(name.indexOf("RRH") + 4, name.length()));
            line += FRH + "," + RRH + ",";
        }

        line += df + "," + drag + "," + conl + ",";
        line += FW + "," + FWMain + "," + FWFlaps + ",NA,";
        line += UT + ",NA," + UTMain + "," + UTTrailing + ",";
        line += RW + "," + RWMain + "," + RWFlaps + "," + RWBiplane + ",NA,";
        line += canard + ","+ diffuser + ","+ cl + ","+ cd + ","+ round(cl/cd, 2) + "," + round(2*fa, 2);

        try {
            FileWriter fw = new FileWriter(reportPath, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(line);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            println("Failed to append reports.");
        }
    }

    private void println(String str){ simulation.println(str); }

    private Double round(Double in, int decPlaces) { return Math.round(in * Math.pow(10, decPlaces)) / Math.pow(10, decPlaces); }

    public void execute() {}
}

public class STARCCM_20_04_007R8_MassExecute extends StarMacro {

    /*
     * 
     * 
     * ONLY CHANGE THE BELOW VALUES
     * 
     * 
     */

    private double maxIterations = 750; // iterations to end sim at (maximum)
    private double maxVelStopper = 1800; // max velocity to end sim at (maximum)

    private boolean onlyMeshing = false; // setting to true will only volume mesh the files, false will start iterations

    /*
     * 
     * 
     * DO NOT CHANGE VALUES BELOW THIS
     * 
     * 
     */

    private Simulation startingSimulation;

    private boolean sabal = false;

    public void execute() {
        this.startingSimulation = getActiveSimulation();
        this.sabal = true;//this.startingSimulation.getSessionPath().contains("/"); // if it uses /, its on unix, inconsistent for some reason not worth the time

        println("\n");

        String dirPath = promptUserForInput("Directory Path", sabal ? "/e/08/rcgsprtn01/SR-17/" : "K:\\Spartan Racing-Formula SAE\\Spartan Racing\\SR-17\\01. Aero Master"); // get dir path from user, autofill based on if on pc or sabal
        if (!dirPath.endsWith(sabal ? "/" : "\\")) { dirPath = dirPath + (sabal ? "/" : "\\"); }
        
        ArrayList<String> simPaths = findSims(dirPath);
        if (simPaths == null) { println("Failed to find sims, invalid path."); return; }
        if (simPaths.isEmpty()) { println("Failed to find sims."); return; }
        println("\nFound " + simPaths.size() + " sims");

        try { 
            File reports = new File(dirPath + "reports.txt");
            if (!reports.exists()) { reports.createNewFile(); println("Created report file"); }
            else { println("Found reports file"); }
        } catch(Exception e) { println("Failed to create reports file."); return; }

        double avgDur = 0;
        int i = 0;

        ArrayList<String> fails = new ArrayList<String>();

        double globalstartms = System.currentTimeMillis();

        for (String path : simPaths) {
            File simFile = new File(path); // used for checking size before trying to mesh
            boolean failure = false;
            String name = path.substring(path.lastIndexOf(sabal ? "/" : "\\") + 1, path.length()-4); // sim name, filename - .sim
            i = i + 1;
            double startms = System.currentTimeMillis(); // record start time of execution

            println("\nOpening " + name + " as Star " + (i + 1));

            Sim sim = null;
            try { sim = new Sim(path); } catch (Exception e) { println("Failed to open sim."); fails.add(name); continue; } // try to open sim file
            
            if (!onlyMeshing) { try { // set stopping criteria
                sim.setStopCriteria(maxVelStopper, maxIterations); 
            } catch (Exception e) { println("Failed to set stopping criteria, skipping."); fails.add(name); continue; } }

            if (!onlyMeshing) { try { // set reports
                sim.setReports(); 
            } catch (Exception e) { println("Failed to set reports, skipping sim."); sim.kill(); fails.add(name); continue; } }

            if (simFile.length() / 1048576 < 1500){ // if sim file is less than 1500MB, try to mesh
                println("Beginning meshing...");
                try { sim.executeMeshing(); } catch (Exception e) { println("Meshing failed, skipping sim."); sim.kill(); fails.add(name); continue; }
            }

            if (!onlyMeshing) { try { // start iterations
                println("Beginning iterations..."); 
                sim.runSim(); 
            } catch (Exception e) { // if iterations fail, try mesh repair then restart iterations
                println("Iterations failed, attempting mesh repair..."); 
                try { sim.repairMesh(); } catch (Exception e2) { println("Repair failed, skipping sim."); sim.kill(); fails.add(name);  continue; } 

                println("Successfully repaired mesh, beginning iterations...");
                try { sim.runSim(); } catch (Exception e2) { println("Iterations failed, skipping sim."); sim.kill(); fails.add(name); continue; }
            } }

            if (!onlyMeshing) { try { // generate and save reports
                println("Generating reports..."); 
                sim.appendReports(dirPath + "reports.txt"); 
            } catch (Exception e) { println("Failed to save reports."); fails.add(name); failure = true; } }

            println("Saving sim...");
            try { sim.save(); } catch (Exception e) { println("Failed to save sim."); if (!failure) { fails.add(name); } failure = true; } // save sim file

            if (!failure) { // if there wasn't a failure, say completed w/ stats
                double dur = ((double)System.currentTimeMillis() - (double)startms) / 1000;
                avgDur = (avgDur * (i - fails.size() - 1) + dur) / (i - fails.size()); 
                double coreHours = startingSimulation.getNumberOfWorkers() * dur / 3600;
                println("Completed " + name + " in " + time(dur) + " using " + round(coreHours, 1) + " core hours");
            } else { // if it failed during reports or saving, something weird happened
                println("Simulation " + name + " failed, unknown reason.");
            }
            println("Estimated Time Remaining: " + time(avgDur * (simPaths.size() - i))); // average dur * sims remaining
            
            try { sim.kill(); } catch (Exception e) { println("Failed to kill sim."); }
        }

        double globaldur = ((double)System.currentTimeMillis() - (double)globalstartms) / 1000; // # of seconds since execution started
        double totalCoreHours = startingSimulation.getNumberOfWorkers() * globaldur / 3600; // seconds spent * cores used / 3600 sec/hr
        println("\n\nSimulations completed. Executed " + simPaths.size() + " sims in " + time(globaldur) + " using " + round(totalCoreHours, 1) + " core hours. " + ((fails.size() > 0) ? (fails.size() + " failures occured.") : ""));
        if (fails.size() > 0) { // if there were failures, list failed sim names
            println("Failed sims: ");
            for (String fail : fails) {
                println(fail);
            }
        }
    }

    public ArrayList<String> findSims(String dirPath) {
        ArrayList<String> ret = new ArrayList<String>();

        File dir = new File(dirPath);
        if (!dir.exists()) { println("Directory doesn't exist"); return null; }
        if (!dir.isDirectory()) { println("Path is not valid directory"); return null; }

        for (File file : dir.listFiles()) {
            String path = file.getPath();
            String name = file.getName().substring(0, file.getName().length()-4); // chop .sim off end of name
            if (path.toLowerCase().endsWith(".sim")) { 
                long fileSizeMB = file.length() / 1048576; // 1024 * 1024
                if (fileSizeMB <= 6500) { // completed halfcar sims are ~7000-7500MB, ignore anything already done
                    ret.add(path);
                    println("Adding " + name + " to queue");
                } else {
                    println("Ignoring " + name + ", already complete");
                }
            }
        }

        return ret;
    }

    private void println(String str){ startingSimulation.println(str); }

    private Double round(Double in, int decPlaces) { return Math.round(in * Math.pow(10, decPlaces)) / Math.pow(10, decPlaces); }

    private String time(double t) {
        String out = "";

        if (t / 3600 > 1){ out += (int)(t / 3600) + "h "; }
        if (t / 60 > 1) { out += (int)((t % 3600) / 60) + "m "; }
        out += (int)(t % 60) + "s";
        
        return out;
    }
}