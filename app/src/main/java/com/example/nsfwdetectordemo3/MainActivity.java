package com.example.nsfwdetectordemo3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nsfwdetectordemo3.ml.Model;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    Button camera, gallery;
    ImageView imageView;
    TextView result;
    TextView resultall;
    int imageSize = 224;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        camera = findViewById(R.id.button);
        gallery = findViewById(R.id.button2);

        result = findViewById(R.id.result);
        resultall = findViewById(R.id.resultall);
        imageView = findViewById(R.id.imageView);

        camera.setOnClickListener(view -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, 3);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
            }
        });
        gallery.setOnClickListener(view -> {
            Intent cameraIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(cameraIntent, 1);
        });
    }

    @SuppressLint("SetTextI18n")
    public void classifyImage(Bitmap image){
        try {
            Model model = Model.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;

            //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
            for(int i = 0; i < imageSize; i ++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f /  255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f /  255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f /  255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Model.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            //duplicate and find order of confidence
            float[] confidences = outputFeature0.getFloatArray();
            float [] originalconfidence = new float[ 5 ];
            System.arraycopy(confidences, 0, originalconfidence, 0, 5);
            String s="";
            String s2="";
            String[] Checking={"Drawing","Hentai","Normal","Porn","Sexy"};
            Arrays.sort(confidences);

            //find highest confidence
            float higestConfidenceValue = confidences[4];
            int indexOfFirstConfidence=0;
            for(int i=0;i<=4;i++)
            {
                if(originalconfidence[i]==higestConfidenceValue)
                {
                    indexOfFirstConfidence=i;
                    break;
                }
            }

            //find 2nd highest confidence
            float secondHigestConfidenceValue = confidences[3];
            int indexOfSecondConfidence=0;
            for(int i=0;i<=4;i++)
            {
                if(originalconfidence[i]==secondHigestConfidenceValue)
                {
                    indexOfSecondConfidence=i;
                    break;
                }
            }

            //print all confidences
            s2="Drawing "+originalconfidence[0]+ "\n" + "Hentai "+originalconfidence[1]+ "\n" + "Normal "+originalconfidence[2]+ "\n" + "Porn "+originalconfidence[3]+ "\n" + "Sexy "+originalconfidence[4];

            //print the highest confidences
            if(higestConfidenceValue>=0.6000000) {
                if(indexOfFirstConfidence==0)
                {
                    s = "This image appears to be a drawing";
                }
                else if (indexOfFirstConfidence==1)
                {
                    s = "Baka!! Hentai!!";
                }
                else if (indexOfFirstConfidence==2)
                {
                    s = "This image appears to be a normal image";
                }
                else if (indexOfFirstConfidence==3)
                {
                    s = "This image appears to be pornographic in nature";
                }
                else if (indexOfFirstConfidence==4)
                {
                    s = "This image appears to be sexy, but not necessarily pornographic";
                }
                else
                {
                    s = "There seems to be an error, try again";
                }
            }
            else if (indexOfFirstConfidence==4 && originalconfidence[4]-originalconfidence[3]<0.2)
            {
                s = "This image appears to be pornographic in nature";
            }
            else if (indexOfFirstConfidence==3 && indexOfSecondConfidence==4 && higestConfidenceValue>0.2000000 && secondHigestConfidenceValue>0.2000000)
            {
                s = "This image appears to be sexy, but not necessarily pornographic";
            }
            else
            {
                s = Checking[indexOfFirstConfidence] + "->" + confidences[4] + "\n" + Checking[indexOfSecondConfidence] + "->" + confidences[3];
            }

            result.setText(s);
            resultall.setText(s2);

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == RESULT_OK){
            assert data != null;
            if(requestCode == 3){
                Bitmap image = (Bitmap) data.getExtras().get("data");
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
                imageView.setImageBitmap(image);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }else{
                Uri dat = data.getData();
                Bitmap image = null;
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), dat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageView.setImageBitmap(image);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}