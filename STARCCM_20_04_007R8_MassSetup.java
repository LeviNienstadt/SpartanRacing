// Made for Siemens STAR-CCM+ 20.04.007-R8 by Levi Nienstadt
// If help is needed, contact via Slack, XXXXXXXXXX@gmail.com, or (XXX) XXX-XXXX

package macro;

import java.util.*;
import java.io.*;
import java.lang.Math.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;
import star.vis.*;
import star.meshing.*;
import star.coremodule.ui.*;

class CAD {
    public String fileName;
    public String filePath;
    public String cadName;

    public CAD(File cad) {
        this.fileName = cad.getName();
        this.filePath = cad.getPath();
        this.cadName = this.fileName.substring(0, this.fileName.length()-4);
    }
}

class Sim {
    public CAD cad;
    public Simulation simulation;

    public double speed;
    private double wheelSpeed;

    private boolean carLoaded;
    private boolean carSetup;

    private CompositePart car;
    private Collection<GeometryPart> carParts;
    private ArrayList<GeometryPart> aero;
    private ArrayList<GeometryPart> foils;
    private ArrayList<GeometryPart> allParts;
    private GeometryPart windTunnel;
    
    private star.common.Region subtract;

    public Sim(CAD c, String simTemplate) {
        cad = c;

        this.simulation = new Simulation(simTemplate);

        this.speed = 35.0; // mph
        this.wheelSpeed = this.speed * 21; // rpm

        this.importCad();
        this.carLoaded = this.loadCar();
        simulation.println(carLoaded ? "Loaded car." : "Failed to load car.");

        this.carSetup = false;
    }

    public Sim(CAD c, String simTemplate, double carSpeed) {
        this(c, simTemplate);

        this.speed = carSpeed;
        wheelSpeed = carSpeed * 21; 
    }

    private void importCad() {
        simulation.get(PartImportManager.class).importCadPart2(cad.filePath, "SharpEdges", 30.0, 2, true, 1.0E-5, true, false, true, true, true, false, false);
    }

    private boolean loadCar() {
        Collection<GeometryPart> simParts = simulation.get(SimulationPartManager.class).getParts();
        for (GeometryPart part : simParts) {
            if (part.getClass().getName() == "star.common.CompositePart") {
                car = (CompositePart) part;
            }
        }
        if (car == null) {
            return false; 
        }
        
        car.setPresentationName("Car");

        GeometryPartManager childrenManager = car.getChildParts();
        carParts = childrenManager.getParts();

        aero = new ArrayList<GeometryPart>();
        foils = new ArrayList<GeometryPart>();
        allParts = new ArrayList<GeometryPart>();
        for (GeometryPart part : carParts){
            String partName = part.getPresentationName();
            if (!partName.toLowerCase().contains("body") && !partName.toLowerCase().contains("suspen") && !partName.toLowerCase().contains("wheel")) {
                aero.add(part);

                if (part.getClass().getName() != "star.meshing.CadPart"){
                    Collection<GeometryPart> subParts = ((CompositePart)part).getChildParts().getParts();
                    for (GeometryPart subpart : subParts) {
                        String subpartName = subpart.getPresentationName();
                        if (!subpartName.toLowerCase().contains("endplate") && !subpartName.toLowerCase().contains("mount")) {
                            foils.add(subpart);
                            if (subpart.getClass().getName() != "star.meshing.CadPart"){
                                Collection<GeometryPart> subSubParts = ((CompositePart)subpart).getChildParts().getParts();
                                for (GeometryPart subSubPart : subSubParts) {
                                    foils.add(subSubPart);
                                }
                            }
                        }
                    }
                } else {
                    foils.add(part);
                }
            }
        }

        allParts.add(car);
        for (GeometryPart part : carParts){
            allParts.add(part);

            if (part.getClass().getName() != "star.meshing.CadPart"){
                Collection<GeometryPart> subParts = ((CompositePart)part).getChildParts().getParts();
                for (GeometryPart subpart : subParts) {
                    if (subpart.getClass().getName() != "star.meshing.CadPart"){
                        Collection<GeometryPart> subSubParts = ((CompositePart)subpart).getChildParts().getParts();
                        for (GeometryPart subSubPart : subSubParts) {
                            allParts.add(subSubPart);
                        }
                    }

                    allParts.add(subpart);
                }
            }
        }

        return true;
    }

    private void subtraction() {
        SubtractPartsOperation subtractOp = ((SubtractPartsOperation) simulation.get(MeshOperationManager.class).getObject("Subtract"));
        try { subtract = simulation.getRegionManager().getRegion("Subtract"); } catch (Exception e) {}
        if (subtract != null) {
            simulation.println("Subtract already executed.");
            return;
        }

        windTunnel = ((CadPart) simulation.get(SimulationPartManager.class).getPart("Wind Tunnel"));

        ArrayList<GeometryObject> sub = new ArrayList<GeometryObject>();
        for (GeometryPart part : allParts) {
            sub.add((GeometryObject) part);
        }
        sub.add((GeometryObject) windTunnel);

        subtractOp.getInputGeometryObjects().setQuery(null);
        subtractOp.getInputGeometryObjects().setObjects(sub);
        subtractOp.execute();

        simulation.getRegionManager().newRegionsFromParts(new ArrayList<>(Arrays.<GeometryPart>asList(simulation.get(SimulationPartManager.class).getPart("Subtract"))), "OneRegionPerPart", null, "OneBoundaryPerPartSurface", null, RegionManager.CreateInterfaceMode.BOUNDARY, "OneEdgeBoundaryPerPart", null);
    }

    private void offsets(boolean exec) {
        MeshOperation offset36 = simulation.get(MeshOperationManager.class).getObject("Offset 36mm");
        MeshOperation offset72 = simulation.get(MeshOperationManager.class).getObject("Offset 72mm");
        MeshOperation offset220 = simulation.get(MeshOperationManager.class).getObject("Offset 220mm");

        offset36.getInputGeometryObjects().setObjects(aero);
        offset72.getInputGeometryObjects().setObjects(car);
        offset220.getInputGeometryObjects().setObjects(car);

        if (exec) {
            offset36.execute();
            offset72.execute();
            offset220.execute();
        }
    }

    private void regions(double speed) {
        subtract = simulation.getRegionManager().getRegion("Subtract");

        Collection<Boundary> boundaries = subtract.getBoundaryManager().getBoundaries();
        Units mph = simulation.getUnitsManager().getObject("mph");
        Units rpm = simulation.getUnitsManager().getObject("rpm");

        double wheelspeed = speed * 21;

        for (Boundary boundary : boundaries) {
            String name = boundary.getPresentationName().toLowerCase();

            if (name.contains("symmetry")) {
                boundary.setBoundaryType(SymmetryBoundary.class);
            }

            if (name.contains("outlet")) {
                boundary.setBoundaryType(PressureBoundary.class);
            }

            if (name.contains("inlet")) {
                boundary.setBoundaryType(InletBoundary.class);
                boundary.getValues().get(VelocityMagnitudeProfile.class).getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(speed, mph);
            }

            if (name.contains("ground")) { // ground & ground car
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.VECTOR);
                boundary.getValues().get(WallRelativeVelocityProfile.class).getMethod(ConstantVectorProfileMethod.class).getQuantity().setComponentsAndUnits(speed, 0.0, 0.0, mph);
            }

            if (name.contains("fl.faces")) {
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.LOCAL_ROTATION_RATE);
                boundary.getValues().get(LocalAxis.class).getModelPartValue().setCoordinateSystem(simulation.getCoordinateSystemManager().getLabCoordinateSystem().getLocalCoordinateSystemManager().getObject("FL"));
                boundary.getValues().get(WallRelativeRotationProfile.class).getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(wheelspeed, rpm);
            }

            if (name.contains("rl.faces")) {
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.LOCAL_ROTATION_RATE);
                boundary.getValues().get(LocalAxis.class).getModelPartValue().setCoordinateSystem(simulation.getCoordinateSystemManager().getLabCoordinateSystem().getLocalCoordinateSystemManager().getObject("RL"));
                boundary.getValues().get(WallRelativeRotationProfile.class).getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(wheelspeed, rpm);
            }
        }
    }

    private void derivedParts() {
        PlaneSection x = (PlaneSection)simulation.getPartManager().getObject("x");
        PlaneSection y = (PlaneSection)simulation.getPartManager().getObject("y");
        PlaneSection z = (PlaneSection)simulation.getPartManager().getObject("z");

        x.getInputParts().setQuery(null);
        x.getInputParts().setObjects(subtract);

        y.getInputParts().setQuery(null);
        y.getInputParts().setObjects(subtract);

        z.getInputParts().setQuery(null);
        z.getInputParts().setObjects(subtract);
    }

    private void autoMesh(boolean exec) {
        AutoMeshOperation autoMeshOp = (AutoMeshOperation) simulation.get(MeshOperationManager.class).getObject("Automated Mesh");
        SurfaceCustomMeshControl surfaceMesh = (SurfaceCustomMeshControl) autoMeshOp.getCustomMeshControls().getObject("Surface Control 2 wings");

        MeshOperationPart subtractPart = (MeshOperationPart) simulation.get(SimulationPartManager.class).getPart("Subtract");

        ArrayList<GeometryObjectProxy> geometryObjects = new ArrayList<GeometryObjectProxy>();
        for (GeometryPart part : foils) {
            geometryObjects.add(subtractPart.getOrCreateProxyForObject(part));
        }

        surfaceMesh.getGeometryObjects().setQuery(null);
        surfaceMesh.getGeometryObjects().setObjects(geometryObjects);

        if (exec) {
            autoMeshOp.executeSurfaceMeshers();
            //autoMeshOp.execute(); dont want to do volume mesh for mass setup
        }
    }

    private void reports() { // following SR17_DDR_template.sim
        subtract = simulation.getRegionManager().getRegion("Subtract");
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

        for (Boundary boundary : boundaries) {
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

    public void setup(boolean exec) {
        if (!this.carLoaded) {
            simulation.println("Car isn't loaded, aborting setup.");
            return;
        }

        simulation.println("Starting subtraction.");
        subtraction();
        simulation.println("Completed subtraction.");

        simulation.println("Starting regions.");
        regions(this.speed);
        simulation.println("Completed regions.");

        simulation.println("Starting derived parts.");
        derivedParts();
        simulation.println("Completed derived parts.");

        simulation.println("Starting offsets.");
        offsets(exec);
        simulation.println("Completed offsets.");
        
        simulation.println("Starting automesh.");
        autoMesh(exec);
        simulation.println("Completed automesh.");

        simulation.println("Starting reports.");
        reports();
        simulation.println("Completed primary reports");

        this.carSetup = true;
    }

    public void save(String path) {
        simulation.saveState(path);
    }

    public void kill() {
        simulation.kill();
    }

    public void execute() {}
}

public class STARCCM_20_04_007R8_MassSetup extends StarMacro {

    private Simulation startingSimulation;
    private ArrayList<CAD> cads;
    private ArrayList<Sim> sims;

    public void execute() {
        startingSimulation = getActiveSimulation();

        String simPath = promptUserForInput("Sim Template Path", "K:\\Spartan Racing-Formula SAE\\Spartan Racing\\SR-17\\01. Aero Master\\4. CFD\\SR17_Levi\\Aeromap\\SR17_DDR_template.sim"); // "K:\Spartan Racing-Formula SAE\Spartan Racing\SR-17\01. Aero Master\4. CFD\SR17_Baseline Revised\SR17 REVISED SIM TEMPLATE.sim";
        String cadPath = promptUserForInput("Directory Path", "K:\\Spartan Racing-Formula SAE\\Spartan Racing\\SR-17\\01. Aero Master\\4. CFD\\SR17_Levi\\Aeromap\\");
        if (!cadPath.endsWith("\\")) { cadPath = cadPath + "\\"; }
        
        findCads(cadPath);
        println("Found " + cads.size() + " CADs");

        new File(cadPath + "Setup").mkdir();
        
        double avgDur = 0;
        int i = 0;
        for (CAD cad : cads) {
            i = i + 1;

            Sim sim = new Sim(cad, simPath, 35); // 35 mph

            println("\nStarting setup for " + sim.cad.cadName + "");
            long startms = System.currentTimeMillis();

            sim.setup(true);
            
            sim.save(cadPath + "Setup\\" + sim.cad.cadName + ".sim");

            double dur = ((double)System.currentTimeMillis() - (double)startms) / 1000;
            avgDur = (avgDur * (i - 1) + dur) / i;
            println("Completed " + sim.cad.cadName + " setup in " + time(dur));
            println("Estimated Time Remaining: " + time(avgDur * (cads.size() - i)));

            sim.kill();
        }
    }

    private void println(String str) { startingSimulation.println(str); }

    private String time(double t) {
        String out = "";
        if (t / 3600 > 1){
            out += (int)(t / 3600) + "h ";
        }

        if (t / 60 > 1) {
            out += (int)((t % 3600) / 60) + "m ";
        }

        out += (int)(t % 60) + "s";
        
        return out;
    }

    private void findCads(String dirPath) {
        cads = new ArrayList<CAD>();

        File dir = new File(dirPath);
        if (!dir.exists()) { println("Directory doesn't exist"); return; }
        if (!dir.isDirectory()) { println("Path is not valid directory"); return; }

        for (File file : dir.listFiles()) {
            String path = file.getPath();
            if (path.toLowerCase().endsWith(".x_t")) { 
                CAD toAdd = new CAD(file);
                cads.add(toAdd);
                println("Found " + toAdd.cadName);
            }
        }
    }
}