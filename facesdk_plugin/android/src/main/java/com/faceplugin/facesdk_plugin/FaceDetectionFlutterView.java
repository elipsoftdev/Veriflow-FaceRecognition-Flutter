package com.faceplugin.facesdk_plugin;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

import com.faceplugin.facesdk_plugin.engine.FaceEngineProvider;
import com.faceplugin.facesdk_plugin.engine.FaceBoxLite;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class FaceDetectionFlutterView implements PlatformView, MethodChannel.MethodCallHandler, CameraViewInterface {

    public static int livenessDetectionLevel = 1; // activo por defecto

    private final MethodChannel channel;
    private final ActivityPluginBinding activityPluginBinding;
    private CameraBaseView cameraView;

    private Handler channelHandler = new Handler(msg -> {
        if (msg.what == 1) {
            @SuppressWarnings("unchecked")
            ArrayList<HashMap<String, Object>> faceBoxesMap = (ArrayList<HashMap<String, Object>>) msg.obj;
            channel.invokeMethod("onFaceDetected", faceBoxesMap);
        }
        return true;
    });

    public FaceDetectionFlutterView(ActivityPluginBinding activityPluginBinding, DartExecutor dartExecutor, int viewId) {
        this.channel = new MethodChannel(dartExecutor, "facedetectionview_" + viewId);
        this.activityPluginBinding = activityPluginBinding;
        this.channel.setMethodCallHandler(this);
        if (cameraView == null) {
            cameraView = new CameraBaseView(activityPluginBinding.getActivity());
            cameraView.setCameraViewInterface(this);
            activityPluginBinding.addRequestPermissionsResultListener(cameraView);
        }
    }

    @Override public View getView() { return cameraView.getView(); }

    @Override public void dispose() {
        if (cameraView != null) {
            activityPluginBinding.removeRequestPermissionsResultListener(cameraView);
            cameraView.dispose();
            cameraView = null;
        }
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final MethodChannel.Result result) {
        switch (call.method) {
            case "startCamera":
                int cameraLens = call.argument("cameraLens");
                cameraView.startCamera(cameraLens);
                result.success(null);
                break;
            case "stopCamera":
                cameraView.stopCamera();
                result.success(null);
                break;
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onFrame(Bitmap bitmap) {
        ArrayList<HashMap<String, Object>> faceBoxesMap = new ArrayList<>();
        List<FaceBoxLite> faceBoxes = FaceEngineProvider.get().faceDetection(bitmap);

        for (int i = 0; i < faceBoxes.size(); i++) {
            FaceBoxLite fb = faceBoxes.get(i);
            byte[] templates = FaceEngineProvider.get().templateExtraction(bitmap, fb);

            Bitmap faceImage = Utils.cropFaceLite(bitmap, fb);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            faceImage.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            byte[] faceJpg = bos.toByteArray();

            HashMap<String, Object> e = new HashMap<>();
            e.put("x1", fb.getX1());
            e.put("y1", fb.getY1());
            e.put("x2", fb.getX2());
            e.put("y2", fb.getY2());
            e.put("liveness", fb.getLivenessScore());
            e.put("yaw", fb.getYaw());
            e.put("roll", fb.getRoll());
            e.put("pitch", fb.getPitch());
            e.put("livenessEvents", fb.getLivenessEvents());
            e.put("templates", templates);
            e.put("faceJpg", faceJpg);
            e.put("frameWidth", bitmap.getWidth());
            e.put("frameHeight", bitmap.getHeight());
            faceBoxesMap.add(e);
        }

        Message message = new Message();
        message.what = 1;
        message.obj = faceBoxesMap;
        channelHandler.sendMessage(message);
    }
}
