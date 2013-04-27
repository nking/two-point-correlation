package algorithms.curves;

/**
 *
 * @author nichole
 */
public interface ICurveGenerator {

    public float[] generateNormalizedCurve(float[] parameters);

    public float[] generateCurve(float[] parameters);
}