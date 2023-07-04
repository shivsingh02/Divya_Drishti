package org.tensorflow.lite.blind.detection.classifiers.behaviors;

public interface ClassifyBehavior {
    float[][] classify(float[] input);
}
