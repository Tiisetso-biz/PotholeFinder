package co.za.letsibogo.potholefinder.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PotholeDetector {

    public static class Detection {
        public final float ymin, xmin, ymax, xmax; // normalized [0..1]
        public final float score;
        public final int classId;

        public Detection(float ymin, float xmin, float ymax, float xmax, float score, int classId) {
            this.ymin = ymin; this.xmin = xmin; this.ymax = ymax; this.xmax = xmax;
            this.score = score; this.classId = classId;
        }

        @Override public String toString() {
            return "det{cls=" + classId + ", score=" + score + ", box=["+ymin+","+xmin+","+ymax+","+xmax+"]}";
        }
    }

    private static final String TAG = "PotholeDetector";

    private Interpreter tflite;
    private int inW = 0, inH = 0, inC = 3;
    private DataType inType = DataType.FLOAT32;
    private boolean shapesLogged = false;

    // Tune these for your model
    private float SCORE_THRESH = 0.50f;
    private int POTHOLE_CLASS_ID = 1;   // change to your pothole class index if different

    public PotholeDetector(Context ctx) {
        try {
            ByteBuffer model = FileUtil.loadMappedFile(ctx, "pothole_model.tflite");
            tflite = new Interpreter(model);

            Tensor inT = tflite.getInputTensor(0);
            int[] ishape = inT.shape();       // [1, H, W, 3] (assumed NHWC)
            inType = inT.dataType();
            inH = ishape[1];
            inW = ishape[2];
            inC = ishape[3];

            if (!shapesLogged) {
                Tensor outT = tflite.getOutputTensor(0);
                Log.d(TAG, "Input shape=" + Arrays.toString(ishape) + " type=" + inType);
                Log.d(TAG, "Output shape=" + Arrays.toString(outT.shape()) + " type=" + outT.dataType());
                shapesLogged = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load tflite", e);
        }
    }

    /** Set score threshold and class id at runtime (optional) */
    public void configure(float scoreThreshold, int potholeClassId) {
        this.SCORE_THRESH = scoreThreshold;
        this.POTHOLE_CLASS_ID = potholeClassId;
    }

    /** Fast boolean: did we detect a pothole? */
    public boolean detect(Bitmap frame) {
        List<Detection> ds = detectWithBoxes(frame);
        for (Detection d : ds) {
            if (d.classId == POTHOLE_CLASS_ID && d.score >= SCORE_THRESH) return true;
        }
        return false;
    }

    /** Full detections list (normalized boxes [0..1]). */
    public List<Detection> detectWithBoxes(Bitmap frame) {
        List<Detection> results = new ArrayList<>();
        if (tflite == null || frame == null) return results;

        Bitmap resized = Bitmap.createScaledBitmap(frame, inW, inH, true);
        ByteBuffer input = prepareInput(resized);

        // Expecting output [1, N, 7]
        Tensor outT = tflite.getOutputTensor(0);
        int[] oshape = outT.shape();
        if (oshape.length != 3 || oshape[0] != 1 || oshape[2] < 6) {
            Log.e(TAG, "Unexpected output shape: " + Arrays.toString(oshape));
            return results;
        }

        float[][][] output = new float[oshape[0]][oshape[1]][oshape[2]];
        try {
            tflite.run(input, output);
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, "Run failed. inputCap=" + input.capacity() + " expected="
                    + bytesPerSample()*inW*inH*inC + " type=" + inType, iae);
            return results;
        }

        // Parse detections. Assumed format: [ymin, xmin, ymax, xmax, score, class, ?]
        int N = oshape[1];
        for (int i = 0; i < N; i++) {
            float ymin  = output[0][i][0];
            float xmin  = output[0][i][1];
            float ymax  = output[0][i][2];
            float xmax  = output[0][i][3];
            float score = output[0][i][4];
            int   cls   = (int) output[0][i][5];

            // Basic sanity: coords in [0,1] and ymin<xmax etc. Skip obvious junk.
            if (score >= SCORE_THRESH &&
                    ymin >= 0 && xmin >= 0 && ymax <= 1.001f && xmax <= 1.001f &&
                    ymax > ymin && xmax > xmin) {
                results.add(new Detection(ymin, xmin, ymax, xmax, score, cls));
            }
        }

        // Optional: NMS could be applied here if boxes overlap; omitted for brevity.

        if (!results.isEmpty()) {
            Log.d(TAG, "Detections: " + Math.min(3, results.size()) + " shown of " + results.size()
                    + " e.g. " + results.get(0));
        }
        return results;
    }

    private int bytesPerSample() { return (inType == DataType.FLOAT32) ? 4 : 1; }

    private ByteBuffer prepareInput(Bitmap bmp) {
        int cap = bytesPerSample() * inW * inH * inC;
        ByteBuffer buf = ByteBuffer.allocateDirect(cap);
        buf.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inW * inH];
        bmp.getPixels(pixels, 0, inW, 0, 0, inW, inH);

        if (inType == DataType.FLOAT32) {
            for (int p : pixels) {
                float r = ((p >> 16) & 0xFF) / 255f;
                float g = ((p >> 8)  & 0xFF) / 255f;
                float b = (p & 0xFF) / 255f;
                buf.putFloat(r);
                buf.putFloat(g);
                buf.putFloat(b);
            }
        } else { // UINT8
            for (int p : pixels) {
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8)  & 0xFF));
                buf.put((byte) (p & 0xFF));
            }
        }
        buf.rewind();
        return buf;
    }
}
