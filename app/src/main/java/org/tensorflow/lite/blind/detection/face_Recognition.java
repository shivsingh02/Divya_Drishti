package org.tensorflow.lite.blind.detection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

//import androidx.room.jarjarred.org.stringtemplate.v4.Interpreter;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class face_Recognition {
    private Interpreter interpreter;
    // define input size
    private int INPUT_SIZE;
    // define height and width of original frame
    private int height = 0;
    private int width = 0;
    // now define Gpudelegate
    // it is use to implement gpu in interpreter
    private GpuDelegate gpuDelegate = null;

    // now define cascadeClassifier for face detection
    private CascadeClassifier cascadeClassifier;

    // now call this in CameraActivity
    face_Recognition(AssetManager assetManager, Context context, String modelPath, int inputSize)
            throws IOException {
        INPUT_SIZE = inputSize;
        // set GPU for the interpreter
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();
        // add gpuDelegate to option
        options.addDelegate(gpuDelegate);
        options.setNumThreads(4); // set this according to your phone
        // this will load model weight to interpreter
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
        // if model is load print
        Log.d("face_Recognition", "Model is loaded");

        try {
            // define input stream to read classifier
            InputStream is=context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            // create a folder
            File cascadeDir=context.getDir("cascade",Context.MODE_PRIVATE);
            // now create a new file in that folder
            File mCascadeFile=new File(cascadeDir,"haarcascade_frontalface_alt");
            // now define output stream to transfer data to file we created
            FileOutputStream os=new FileOutputStream(mCascadeFile);
            // now create buffer to store byte
            byte[] buffer=new byte[4096];
            int byteRead;
            // read byte in while loop
            // when it read -1 that means no data to read
            while ((byteRead=is.read(buffer)) !=-1){
                // writing on mCascade file
                os.write(buffer,0,byteRead);

            }
            // close input and output stream
            is.close();
            os.close();
            cascadeClassifier=new CascadeClassifier(mCascadeFile.getAbsolutePath());
            // if cascade file is loaded print
            Log.d("face_Recognition","Classifier is loaded");
            //Face recognition model,classifier is loaded--> check log

        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    public Mat recognizeImage(Mat mat_image) {
        // before predicting
        // our image is not properly align
        // we have to rotate it by 90 degree for proper prediction
        Core.flip(mat_image.t(), mat_image, 1);// rotate mat_image by 90 degree
        // start with our process
        // convert mat_image to gray scale image
        Mat grayscaleImage = new Mat();
        Imgproc.cvtColor(mat_image, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);
        // set height and width
        height = grayscaleImage.height();
        width = grayscaleImage.width();

        // define minimum height of face in original image
        // below this size no face in original image will show
        int absoluteFaceSize = (int) (height * 0.1);
        // now create MatofRect to store face
        MatOfRect faces = new MatOfRect();
        // check if cascadeClassifier is loaded or not
        if (cascadeClassifier != null) {
            // detect face in frame
            //                                  input         output
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
            // minimum size
        }

        // now convert it to array
        Rect[] faceArray = faces.toArray();
        // loop through each face
        for (int i = 0; i < faceArray.length; i++) {
            // if you want to draw rectangle around face
            //                input/output starting point ending point        color   R  G  B  alpha    thickness
            Imgproc.rectangle(mat_image, faceArray[i].tl(), faceArray[i].br(), new Scalar(0, 255, 0, 255), 2);
            // now crop face from original frame and grayscaleImage
            // starting x coordinate       starting y coordinate
            Rect roi = new Rect((int) faceArray[i].tl().x, (int) faceArray[i].tl().y,
                    ((int) faceArray[i].br().x) - (int) (faceArray[i].tl().x),
                    ((int) faceArray[i].br().y) - (int) (faceArray[i].tl().y));
            // it's very important check one more time
            Mat cropped_rgba = new Mat(mat_image, roi);//
            // now convert cropped_rgba to bitmap
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(cropped_rgba.cols(), cropped_rgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgba, bitmap);
            // resize bitmap to (48,48)
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, false);
            // now convert scaledBitmap to byteBuffer
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);
            // now create an object to hold output
            float[][] face_value = new float[1][1];
            //now predict with bytebuffer as an input and emotion as an output
            interpreter.run(byteBuffer, face_value);
            // if emotion is recognize print value of it
            float read_face=(float)Array.get(Array.get(face_value,0),0);
            Log.d("face_Recognition", "Output:  " + read_face);
            String face_name=get_face_name(read_face);
            Imgproc.putText(mat_image,""+face_name,
                    new Point((int)faceArray[i].tl().x+10,(int)faceArray[i].tl().y+20),
                    1,1.5,new Scalar(255,255,255,150),2);

        }

        // after prediction
        // rotate mat_image -90 degree
        Core.flip(mat_image.t(), mat_image, 0);
        return mat_image;
    }

    private String get_face_name(float read_face) {
        String val="";
        if(read_face>=0 & read_face<0.5){
            val="Courteney Cox";
        }
        else if(read_face>=0.5 & read_face<1.5){
            val="Arnold Schwarzenegger";
        }
        else if(read_face>=1.5 & read_face<2.5){
            val="Bhuvan Bam";
        }
        else if(read_face>=2.5 & read_face<3.5){
            val="Hardik Pandya";
        }
        else if(read_face>=3.5 & read_face<4.5){
            val="David Schwimmer";
        }
        else if(read_face>=4.5 & read_face<5.5){
            val="Matt LeBlanc";
        }
        else if(read_face>=5.5 & read_face<6.5){
            val="Simon Helberg";
        }
        else if(read_face>=6.5 & read_face<7.5){
            val="Scarlett Johansson";
        }
        else if(read_face>=7.5 & read_face<8.5){
            val="Pankaj Tripathi";
        }
        else if(read_face>=8.5 & read_face<9.5){
            val="Matthew Perry";
        }
        else if(read_face>=9.5 & read_face<10.5){
            val="Sylvester Stallone";
        }
        else if(read_face>=10.5 & read_face<11.5){
            val="Messi";
        }
        else if(read_face>=11.5 & read_face<12.5){
            val="Jim Parsons";
        }
        else if(read_face>=12.5 & read_face<13.5){
            val="Not in Dataset";
        }
        else if(read_face>=13.5 & read_face<14.5){
            val="Lisa Kudrow";
        }
        else if(read_face>=14.5 & read_face<15.5){
            val="Mohmad Ali";
        }
        else if(read_face>=15.5 & read_face<16.5){
            val="Brad Pitt";
        }
        return val;


    }


    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {
        ByteBuffer byteBuffer;
        int size_image=INPUT_SIZE;//96

        byteBuffer=ByteBuffer.allocateDirect(4*1*size_image*size_image*3);
        // 4 is multiplied for float input
        // 3 is multiplied for rgb
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_image*size_image];
        scaledBitmap.getPixels(intValues,0,scaledBitmap.getWidth(),0,0,
                scaledBitmap.getWidth(),scaledBitmap.getHeight());
        int pixel=0;
        for(int i =0;i<size_image;++i){
            for(int j=0;j<size_image;++j){
                final int val=intValues[pixel++];
                // now put float value to bytebuffer
                // scale image to convert image from 0-255 to 0-1
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val & 0xFF))/255.0f);

            }
        }
        return byteBuffer;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException{
        // this will give description of file
        AssetFileDescriptor assetFileDescriptor=assetManager.openFd(modelPath);
        // create an inputstream to read file
        FileInputStream inputStream=new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();

        long startOffset=assetFileDescriptor.getStartOffset();
        long declaredLength=assetFileDescriptor.getDeclaredLength();
        return  fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);

    }
    }
