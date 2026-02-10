// Made for Siemens STAR-CCM+ 20.04.007-R8 by Levi Nienstadt
// If help is needed, contact via Slack, XXXXXXXXXX@gmail.com, or (XXX) XXX-XXXX

// To make changes, all changeable elements are at the top of the class below

package macro;

import java.util.*;

import javax.swing.plaf.synth.Region;

import java.io.*;
import java.lang.Math.*;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;
import star.vis.*;
import star.meshing.*;

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

public class STARCCM_20_04_007R8_SimSetup extends StarMacro {

    /*
     * 
     * 
     * ONLY CHANGE THE BELOW VALUES
     * 
     * 
     */

    private boolean ExecuteOperations = true; // set to false to disable all operation execution and have the script only set values (subtract will still execute but only if there is not already a subtract done)

    private double speed = 35.0; // mph, wheelspeed is calculated as speed * 21 rpm, remember the decimal point if changing

    private List<String> AeroFilters = Arrays.asList("body", "suspen", "wheel"); // any parts containing these keywords are considered not aero or part of aero devices
    private List<String> FoilFilters = Arrays.asList("endplate", "mount"); // any parts that passed AeroFilters and do not contain any of these will be considered an airfoil. All foils are passed to the 36mm offset.
    private List<String> OffsetFilters = Arrays.asList("sp main", "sp strakes", "ut main", "ut strakes"); // any foils that do not contain any of these will be used in the 6.35mm offset.

    private String frontLeftWheel = "fl.faces"; // the target for setting wheel regions for the front left
    private String rearLeftWheel = "rl.faces"; // the target for setting wheel regions for the rear left

    private String windTunnelName = "wind tunnel"; // name of the wind tunnel object, used for setting reports and subtracts
    private List<String> fwFilter = Arrays.asList(".fw"); // any surfaces containing these keywords will be considered part of FW reports
    private List<String> rwFilter = Arrays.asList(".rw"); // any surfaces containing these keywords will be considered part of RW reports
    private List<String> utFilter = Arrays.asList(".ut", ".sp"); // any surfaces containing these keywords will be considered part of UT reports
    private List<String> canardFilter = Arrays.asList("canard"); // any surfaces containing these keywords will be considered part of UT reports
    private List<String> diffuserFilter = Arrays.asList("diffuser"); // any surfaces containing these keywords will be considered part of UT reports

    private double TkeStopCriteria = 4.0E-7; // tke to stop sim at (minimum)
    private double MaxVelStopCritera = 1500; // max velocity to stop sim at (maximum)

    /*
     * 
     * 
     * DO NOT CHANGE VALUES BELOW THIS
     * 
     * 
     */

    private boolean executeVolMesh = false;

    private Simulation simulation;

    private CompositePart car;
    private Collection<GeometryPart> carParts;
    private ArrayList<GeometryPart> aero;
    private ArrayList<GeometryPart> foils;
    private ArrayList<GeometryPart> allParts;
    private GeometryPart windTunnel;
    
    private star.common.Region subtract;

    public void execute() {
        simulation = getActiveSimulation();

        if (!loadCar(false)) { simulation.println("Error loading car."); return; }

        if (ExecuteOperations) {
            String volMesh = promptUserForInput("Execute Volume Mesh? (y/n) (Not Recommended for Laptops)", "").toLowerCase();
            executeVolMesh = (volMesh.compareTo("y") == 0 || volMesh.compareTo("yes") == 0);
        }

        simulation.println("Starting sim setup.\n");
        long startms = System.currentTimeMillis();

        simulation.println("Starting subtraction.");
        subtraction();
        simulation.println("Completed subtraction.");

        simulation.println("Starting regions.");
        regions();
        simulation.println("Completed regions.");

        simulation.println("Starting derived parts.");
        derivedParts();
        simulation.println("Completed derived parts.");

        simulation.println("Starting offsets.");
        offsets();
        simulation.println("Completed offsets.");
        
        simulation.println("Starting automesh.");
        autoMesh();
        simulation.println("Completed automesh.");

        simulation.println("Setting stopping criterion.");
        setStopCriteria();
        simulation.println("Set stopping criterion.");

        simulation.println("Validating mesh.");
        repairMesh();
        simulation.println("Validated mesh.");

        simulation.println("Starting reports."); // only primary reports, individual parts will be done in ddr scripts
        reports();
        simulation.println("Completed primary reports.");
        simulation.println("DON'T FORGET TO SET INDIVIDUAL COMPONENT REPORTS");

        double dur = ((double)System.currentTimeMillis() - (double)startms) / 1000;
        simulation.println("\nCompleted setup in " + (int)(dur / 60) + " minutes " + (int)(dur % 60) + " seconds.");
        
        saveFile();
    }

    private boolean contains(String str, List<String> checks) { // checking for multiple strings in a str
        for (String check : checks) {
            if (str.toLowerCase().contains(check.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    private boolean loadCar(boolean retry) {
        Collection<GeometryPart> simParts = simulation.get(SimulationPartManager.class).getParts();
        for (GeometryPart part : simParts) {
            if (part.getClass().getName() == "star.common.CompositePart") {
                car = (CompositePart) part;
            }
        }
        if (car == null) { 
            if (retry) {
                simulation.println("Import your cad you ape");
                return false; 
            }

            try {
                simulation.println("No CAD imported, attempting to search for parasolid.");
                String path = simulation.getSessionPath();
                path = path.substring(0, path.length() - 3) + "x_t";
                simulation.println("Searching for " + path);
                simulation.get(PartImportManager.class).importCadPart2(resolvePath(path), "SharpEdges", 30.0, 2, true, 1.0E-5, true, false, true, true, true, false, false);
            } catch (Exception e) {
                simulation.println("Import your cad you ape");
                return false; 
            }

            loadCar(true);
        }
        
        car.setPresentationName("Car");

        GeometryPartManager childrenManager = car.getChildParts();
        carParts = childrenManager.getParts();

        aero = new ArrayList<GeometryPart>();
        foils = new ArrayList<GeometryPart>();
        allParts = new ArrayList<GeometryPart>();
        for (GeometryPart part : carParts){
            String partName = part.getPresentationName();
            if (!contains(partName, AeroFilters)) {
                aero.add(part);

                if (part.getClass().getName() != "star.meshing.CadPart"){
                    Collection<GeometryPart> subParts = ((CompositePart)part).getChildParts().getParts();
                    for (GeometryPart subpart : subParts) {
                        String subpartName = subpart.getPresentationName();
                        if (!contains(subpartName, FoilFilters)) {
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

    private void offsets() {
        MeshOperation offset6 = null;
        try { offset6 = simulation.get(MeshOperationManager.class).getObject("Offset 6.35mm"); } catch (Exception e) {}
        MeshOperation offset36 = simulation.get(MeshOperationManager.class).getObject("Offset 36mm");
        MeshOperation offset72 = simulation.get(MeshOperationManager.class).getObject("Offset 72mm");
        MeshOperation offset220 = simulation.get(MeshOperationManager.class).getObject("Offset 220mm");

        ArrayList<GeometryPart> filteredFoils = new ArrayList<GeometryPart>();
        for (GeometryPart foil : foils) {
            String foilName = foil.getPresentationName();

            String parentName = "";
            try {
                GeometryPart parent = foil.getParentPart();
                parentName = parent.getPresentationName();
            } catch (Exception e) {}

            if (!contains(foilName, OffsetFilters) && !contains(parentName, OffsetFilters)) {
                filteredFoils.add(foil);
            }
        }

        if (offset6 != null) { offset6.getInputGeometryObjects().setObjects(filteredFoils); }
        offset36.getInputGeometryObjects().setObjects(aero);
        offset72.getInputGeometryObjects().setObjects(car);
        offset220.getInputGeometryObjects().setObjects(car);

        if (ExecuteOperations) {
            if (offset6 != null) { offset6.execute(); }
            offset36.execute();
            offset72.execute();
            offset220.execute();
        }
    }

    private void regions() {
        subtract = simulation.getRegionManager().getRegion("Subtract");

        Collection<Boundary> boundaries = subtract.getBoundaryManager().getBoundaries();
        Units mph = simulation.getUnitsManager().getObject("mph");
        Units rpm = simulation.getUnitsManager().getObject("rpm");

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

            if (name.contains("ground")) { // both ground & ground car
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.VECTOR);
                boundary.getValues().get(WallRelativeVelocityProfile.class).getMethod(ConstantVectorProfileMethod.class).getQuantity().setComponentsAndUnits(speed, 0.0, 0.0, mph);
            }

            if (name.contains(frontLeftWheel)) {
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.LOCAL_ROTATION_RATE);
                boundary.getValues().get(LocalAxis.class).getModelPartValue().setCoordinateSystem(simulation.getCoordinateSystemManager().getLabCoordinateSystem().getLocalCoordinateSystemManager().getObject("FL"));
                boundary.getValues().get(WallRelativeRotationProfile.class).getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(speed * 21, rpm);
            }

            if (name.contains(rearLeftWheel)) {
                boundary.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.LOCAL_ROTATION_RATE);
                boundary.getValues().get(LocalAxis.class).getModelPartValue().setCoordinateSystem(simulation.getCoordinateSystemManager().getLabCoordinateSystem().getLocalCoordinateSystemManager().getObject("RL"));
                boundary.getValues().get(WallRelativeRotationProfile.class).getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(speed * 21, rpm);
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

    private void autoMesh() {
        AutoMeshOperation autoMeshOp = (AutoMeshOperation) simulation.get(MeshOperationManager.class).getObject("Automated Mesh");
        SurfaceCustomMeshControl surfaceMesh = (SurfaceCustomMeshControl) autoMeshOp.getCustomMeshControls().getObject("Surface Control 2 wings");

        MeshOperationPart subtractPart = (MeshOperationPart) simulation.get(SimulationPartManager.class).getPart("Subtract");

        ArrayList<GeometryObjectProxy> geometryObjects = new ArrayList<GeometryObjectProxy>();
        for (GeometryPart part : foils) {
            geometryObjects.add(subtractPart.getOrCreateProxyForObject(part));
        }

        surfaceMesh.getGeometryObjects().setQuery(null);
        surfaceMesh.getGeometryObjects().setObjects(geometryObjects);

        if(ExecuteOperations) {
            autoMeshOp.executeSurfaceMeshers();
            if (executeVolMesh) { autoMeshOp.execute(); }
        }
    }

    public void repairMesh() { // attempt to remove invalid cells from subtract region
        simulation.getMeshManager().removeInvalidCells(new ArrayList<>(Arrays.<star.common.Region>asList(simulation.getRegionManager().getRegion("Subtract"))), NeoProperty.fromString("{\'minimumContiguousFaceArea\': 0.0, \'minimumCellVolumeEnabled\': true, \'minimumVolumeChangeEnabled\': true, \'functionOperator\': 0, \'minimumContiguousFaceAreaEnabled\': true, \'minimumFaceValidityEnabled\': true, \'functionValue\': 0.0, \'functionEnabled\': false, \'function\': \'\', \'minimumVolumeChange\': 1.0E-10, \'minimumCellVolume\': 0.0, \'minimumCellQualityEnabled\': true, \'minimumCellQuality\': 1.0E-8, \'minimumDiscontiguousCells\': 1, \'minimumDiscontiguousCellsEnabled\': true, \'minimumFaceValidity\': 0.51}"));
    }

    private void reports() { // individual component reports will be done for DDRs due to changing parts, for now with changing parts just manually do them
        subtract = simulation.getRegionManager().getRegion("Subtract");
        Collection<Boundary> boundaries = subtract.getBoundaryManager().getBoundaries();

        ArrayList<Boundary> carFaces = new ArrayList<Boundary>();
        ArrayList<Boundary> FW = new ArrayList<Boundary>();
        ArrayList<Boundary> RW = new ArrayList<Boundary>();
        ArrayList<Boundary> UT = new ArrayList<Boundary>();
        ArrayList<Boundary> Canard = new ArrayList<Boundary>();
        ArrayList<Boundary> Diffuser = new ArrayList<Boundary>(); 

        for (Boundary boundary : boundaries) {
            String boundaryName = boundary.getPresentationName().toLowerCase();
            if (!boundaryName.contains(windTunnelName)) {
                carFaces.add(boundary);

                if (contains(boundaryName, fwFilter)) {
                    FW.add(boundary);
                }

                if (contains(boundaryName, rwFilter)) {
                    RW.add(boundary);
                }

                if (contains(boundaryName, utFilter)) {
                    UT.add(boundary);
                }

                if (contains(boundaryName, canardFilter)) {
                    Canard.add(boundary);
                }

                if (contains(boundaryName, diffuserFilter)) {
                    Diffuser.add(boundary);
                }
            }
        }

        ForceReport drag = (ForceReport) simulation.getReportManager().getReport("1 Drag total");
        ForceReport df = (ForceReport) simulation.getReportManager().getReport("1 Downforce total");
        ForceReport dfFW = (ForceReport) simulation.getReportManager().getReport("DF FW");
        ForceReport dfRW = (ForceReport) simulation.getReportManager().getReport("DF RW");
        ForceReport dfUT = (ForceReport) simulation.getReportManager().getReport("DF UT");
        ForceReport dfCanard = (ForceReport) simulation.getReportManager().getReport("DF CANARD");
        ForceReport dfDiffuser = (ForceReport) simulation.getReportManager().getReport("DF DIFFUSER");
        ForceReport dragFW = (ForceReport) simulation.getReportManager().getReport("Drag FW");
        ForceReport dragRW = (ForceReport) simulation.getReportManager().getReport("Drag RW");
        CenterOfLoadsReport clz = (CenterOfLoadsReport) simulation.getReportManager().getReport("Center of Loads z");
        CenterOfLoadsReport clx = (CenterOfLoadsReport) simulation.getReportManager().getReport("Center of Loads x");
        FrontalAreaReport fa = (FrontalAreaReport) simulation.getReportManager().getReport("Frontal Area");
        ForceCoefficientReport cl = (ForceCoefficientReport) simulation.getReportManager().getReport("Cl");
        ForceCoefficientReport cd = (ForceCoefficientReport) simulation.getReportManager().getReport("Cd");
        MaxReport maxVel = (MaxReport) simulation.getReportManager().getReport("1 Maximum velocity");

        drag.getParts().setQuery(null);
        df.getParts().setQuery(null);
        dfFW.getParts().setQuery(null);
        dfRW.getParts().setQuery(null);
        dfUT.getParts().setQuery(null);
        dfCanard.getParts().setQuery(null);
        dfDiffuser.getParts().setQuery(null);
        dragFW.getParts().setQuery(null);
        dragRW.getParts().setQuery(null);
        clz.getParts().setQuery(null);
        clx.getParts().setQuery(null);
        fa.getParts().setQuery(null);
        cl.getParts().setQuery(null);
        cd.getParts().setQuery(null);
        maxVel.getParts().setQuery(null);

        drag.getParts().setObjects(carFaces);
        df.getParts().setObjects(carFaces);
        dfFW.getParts().setObjects(FW);
        dfRW.getParts().setObjects(RW);
        dfUT.getParts().setObjects(UT);
        dfCanard.getParts().setObjects(Canard);
        dfDiffuser.getParts().setObjects(Diffuser);
        dragFW.getParts().setObjects(FW);
        dragRW.getParts().setObjects(RW);
        clz.getParts().setObjects(carFaces);
        clx.getParts().setObjects(carFaces);
        fa.getParts().setObjects(carFaces);
        cl.getParts().setObjects(carFaces);
        cd.getParts().setObjects(carFaces);
        maxVel.getParts().setObjects(subtract);
    }

    private void setStopCriteria() {
        MonitorIterationStoppingCriterion maxVelStopper = simulation.getSolverStoppingCriterionManager().createIterationStoppingCriterion(simulation.getMonitorManager().getMonitor("1 Maximum velocity Monitor"));
        MonitorIterationStoppingCriterion tkeStopper = simulation.getSolverStoppingCriterionManager().createIterationStoppingCriterion(simulation.getMonitorManager().getMonitor("Tke"));

        ((MonitorIterationStoppingCriterionOption) maxVelStopper.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.MAXIMUM);
        ((MonitorIterationStoppingCriterionOption) tkeStopper.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.MINIMUM);

        ((MonitorIterationStoppingCriterionMaxLimitType) maxVelStopper.getCriterionType()).getLimit().setValueAndUnits(MaxVelStopCritera, simulation.getUnitsManager().getObject("mph"));
        ((MonitorIterationStoppingCriterionMinLimitType) tkeStopper.getCriterionType()).getLimit().setValueAndUnits(TkeStopCriteria, simulation.getUnitsManager().getObject(""));
    }
    
    public void saveFile() {
        simulation.saveState(simulation.getSessionPath());
    }
}