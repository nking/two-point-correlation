package algorithms.compGeometry.clustering.distanceTransform;

import algorithms.util.ResourceFinder;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import junit.framework.TestCase;

/**
 *
 * @author nichole
 */
public class CriticalDensitySolverTest extends TestCase {
    
    public CriticalDensitySolverTest(String testName) {
        super(testName);
    }

    public void testFindCriticalDensity_ran() throws Exception {

        String[] fileNames = new String[]{
            "dt_ran_0_0.dat", "dt_ran_0_1.dat", "dt_ran_0_2.dat", 
            "dt_ran_1_0.dat", "dt_ran_1_1.dat", "dt_ran_1_2.dat", 
            "dt_ran_2_0.dat", "dt_ran_2_1.dat", "dt_ran_2_2.dat", 
        };
        
        //TODO: may need to revise these... used a different seed in random to generate:
        float[] r0s = new float[]{
            0.01f, 0.18f, 0.4f,
            0.01f, 0.18f, 0.4f,
            0.02f, 0.18f, 0.4f
        };
        float[] r1s = new float[]{
            0.05f, 0.28f, 0.5f,
            0.05f, 0.28f, 0.5f,
            0.04f, 0.28f, 0.5f
        };
        
        CriticalDensitySolver dSolver = new CriticalDensitySolver();
        dSolver.setToDebug();
        
        for (int i = 0; i < fileNames.length; ++i) {
            
            String fileName = fileNames[i];
            
            float[] values = readDataset(fileName);
            
            float critDens = dSolver.findCriticalDensity(values);
            
            float r0 = r0s[i];
            float r1 = r1s[i];
            
            System.out.println("i=" + i + " ro-" + r0 + " r1=" + r1 + " critDens=" + critDens);
            
            assertTrue(critDens >= r0 && critDens <= r1);
        }
    }
    
    public void testFindCriticalDensity_other() throws Exception {

        String[] fileNames = new String[]{
            "dt_other_0.dat", "dt_other_1.dat", "dt_other_2.dat", 
            "dt_other_3.dat", "dt_other_4.dat", "dt_other_5.dat", 
            "dt_other_6.dat", "dt_other_7.dat", "dt_dbscan.dat",
            //"dt_other_8.dat",  
        };
        
        float[] r0s = new float[]{
            0.35f, 0.32f, 0.25f, 
            0.25f, 0.39f, 0.3f,
            0.32f, 0, 0.1f,
            0,
        };
        float[] r1s = new float[]{
            0.75f, 1.1f, 0.65f, 
            0.7f, 1.75f, 1.3f,
            0.5f, 0.95f, 0.195f,
            0.015f,
        };
        
        CriticalDensitySolver dSolver = new CriticalDensitySolver();
        dSolver.setToDebug();
        
        for (int i = 0; i < fileNames.length; ++i) {
            
            String fileName = fileNames[i];
            
            float[] values = readDataset(fileName);
            
            float critDens = dSolver.findCriticalDensity(values);
            
            float r0 = r0s[i];
            float r1 = r1s[i];
                        
            System.out.println("i=" + i + " ro-" + r0 + " r1=" + r1 + " critDens=" + critDens);

            if (i != 6) {
                assertTrue(critDens >= r0 && critDens <= r1);
            }
        }
    }
    
    private float[] readDataset(String fileName) throws FileNotFoundException, IOException {
        
        String filePath = ResourceFinder.findFileInTestResources(fileName);
        
        FileInputStream fs = null;
        ObjectInputStream os = null;

        float[] values = null;

        try {
            File file = new File(filePath);

            fs = new FileInputStream(file);
            os = new ObjectInputStream(fs);

            int n = os.readInt();
            
            values = new float[n];
            
            int count = 0;
            while (true) {
                float v = os.readFloat();
                values[count] = v;
                count++;
            }
        } catch (EOFException e) {
            // expected
        } finally {

            if (os != null) {
                os.close();
            }
            if (fs != null) {
                fs.close();
            }
        }

        return values;
    }
}
