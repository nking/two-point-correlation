package algorithms.compGeometry.clustering.twopointcorrelation;

import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;

/**
 *
 * @author nichole
 */
public class TwoPointBinarySearchTreeTest extends TestCase {

    public TwoPointBinarySearchTreeTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of storeIfDoesNotContain method, of class TwoPointIdentities.
     */
    public void testStoreIfDoesNotContain() {

        int pointIndex0 = 0;
        int pointIndex1 = 10;

        ITwoPointIdentity identities = new TwoPointBinarySearchTree();
        boolean stored = identities.storeIfDoesNotContain(pointIndex0, pointIndex1);
        assertTrue(stored);

        stored = identities.storeIfDoesNotContain(pointIndex0, pointIndex1);
        assertFalse(stored);

        for (int i = 1; i < 121; i++) {
            for (int j = (i + 1); j < 121; j++) {
                stored = identities.storeIfDoesNotContain(i, j);
                assertTrue(stored);
            }
        }

        for (int i = 1; i < 121; i++) {
            for (int j = (i + 1); j < 121; j++) {
                stored = identities.storeIfDoesNotContain(i, j);
                assertFalse(stored);
            }
        }
    }

}