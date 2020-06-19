package infore.SDE.sketches.TimeSeries;

import org.apache.commons.math3.complex.Complex;

public class COEF {
    private String StreamKey;



    private Complex[] fourierCoefficients;

    public COEF(String streamKey, Complex[] fourierCoefficients) {
        StreamKey = streamKey;
        this.fourierCoefficients = fourierCoefficients;
    }

    public String getStreamKey() {
        return StreamKey;
    }

    public Complex[] getFourierCoefficients() {
        return fourierCoefficients;
    }
}