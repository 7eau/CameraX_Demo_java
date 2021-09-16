package com.awo.mycameraxstudy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private int REQUEST_CODE_PERMISSIONS = 10; //arbitrary number, can be changed accordingly
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA","android.permission.WRITE_EXTERNAL_STORAGE"};

    TextureView ttv;// 用以显示预览画面的的View组件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 第一步：设置预览组件的OnLayoutChangeListener（当预览组件的尺寸发生改变时的回调）
        ttv = findViewById(R.id.ttv_camera_preview);
        ttv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // 尺寸变化时确保预览画面的方向始终正确
                updateTransform();
            }
        });
        /*View的宽、高确定后，将在主线程执行run()方法，此处用来启动相机*/
        ttv.post(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });
    }

    private void startCamera() {
        // 清楚所有绑定
        CameraX.unbindAll();

        /**
         * 预览
         */
        // 计算屏幕参数:宽、高 、屏幕高宽比、尺寸
        int aspRatioW = ttv.getWidth(); // 预览View的宽
        int aspRatioH = ttv.getHeight(); // 预览View的高
        Rational asp = new Rational (aspRatioW, aspRatioH); // 屏幕高、宽比
        Size screen = new Size(aspRatioW, aspRatioH); // 屏幕尺寸

        // 通过PreviewConfig注入预览设置
        PreviewConfig pConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(asp)
                .setTargetResolution(screen)
                .build();

        // 根据预览配置生成预览对象，并设置预览回调（每一帧画面都调用一次该回调函数）
        Preview preview = new Preview(pConfig);
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                // 需要移除父组件后重新添加View组件，固定写法
                ViewGroup parent = (ViewGroup) ttv.getParent();
                parent.removeView(ttv);
                parent.addView(ttv, 0);

                ttv.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });

        /**
         * 分析
         */
        // 创建Handler用以在子线程处理数据
        HandlerThread handlerThread = new HandlerThread("Image_Analyze");
        handlerThread.start();
        // 创建ImageAnalysisConfig 配置
        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder()
                .setCallbackHandler(new Handler(handlerThread.getLooper()))
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setTargetAspectRatio(asp)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(new MyAnalyzer());


        // 将当前Activity和preview 绑定生命周期
        CameraX.bindToLifecycle(this, preview, imageAnalysis);
    }

    /**
     * 更新相机预览, 用以保证预览方向正确, 固定写法
     */
    private void updateTransform() {
        Matrix matrix = new Matrix();

        // Compute the center of the view finder
        float centerX = ttv.getWidth() / 2f;
        float centerY = ttv.getHeight() / 2f;

        float[] rotations = {0,90,180,270};
        // Correct preview output to account for display rotation
        float rotationDegrees = rotations[ttv.getDisplay().getRotation()];

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        ttv.setTransform(matrix);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //start camera when permissions have been granted otherwise exit app
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * 检查是否所有所请求的权限都获得许可
     * @return
     */
    private boolean allPermissionsGranted(){
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    /**
     * 自定义Analyzer类, 实现ImageAnalysis.Analyzer接口
     * anylyze()是每一帧画面的回调函数
     */
    private class MyAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy image, int rotationDegrees) {
            // 在这里对每一帧画面进行处理
            final Image img = image.getImage();
            if (img != null) {
                Log.d("MainActivity", "Image Time Stamp is: " + img.getTimestamp());
            }
        }
    }

}