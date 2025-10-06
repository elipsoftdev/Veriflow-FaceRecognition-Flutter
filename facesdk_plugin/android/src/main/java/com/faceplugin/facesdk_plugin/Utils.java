package com.faceplugin.facesdk_plugin;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;

import com.ocp.facesdk.FaceBox; // legado (si ya quitaste el motor, puedes borrar este import)
import com.faceplugin.facesdk_plugin.engine.FaceBoxLite; // nuevo

import java.io.IOException;
import java.io.InputStream;

public class Utils {

    /** Recorte (200x200) usando FaceBox del motor anterior (legado). */
    public static Bitmap cropFace(Bitmap src, FaceBox faceBox) {
        int centerX = (faceBox.x1 + faceBox.x2) / 2;
        int centerY = (faceBox.y1 + faceBox.y2) / 2;
        int cropWidth = (int) ((faceBox.x2 - faceBox.x1) * 1.4f);

        int cropX1 = Math.max(0, centerX - cropWidth / 2);
        int cropY1 = Math.max(0, centerY - cropWidth / 2);
        int cropX2 = Math.min(src.getWidth() - 1, centerX + cropWidth / 2);
        int cropY2 = Math.min(src.getHeight() - 1, centerY + cropWidth / 2);

        float scaleWidth = 200f / (cropX2 - cropX1 + 1);
        float scaleHeight = 200f / (cropY2 - cropY1 + 1);

        Matrix m = new Matrix();
        m.setScale(scaleWidth, scaleHeight);

        return Bitmap.createBitmap(
                src, cropX1, cropY1, (cropX2 - cropX1 + 1), (cropY2 - cropY1 + 1), m, true
        );
    }

    /** Recorte (200x200) usando FaceBoxLite del nuevo motor libre. */
    public static Bitmap cropFaceLite(Bitmap src, FaceBoxLite fb) {
        int centerX = (fb.getX1() + fb.getX2()) / 2;
        int centerY = (fb.getY1() + fb.getY2()) / 2;
        int cropWidth = (int) ((fb.getX2() - fb.getX1()) * 1.4f);

        int x1 = Math.max(0, centerX - cropWidth / 2);
        int y1 = Math.max(0, centerY - cropWidth / 2);
        int x2 = Math.min(src.getWidth() - 1, centerX + cropWidth / 2);
        int y2 = Math.min(src.getHeight() - 1, centerY + cropWidth / 2);

        Matrix m = new Matrix();
        float scaleW = 200f / (x2 - x1 + 1);
        float scaleH = 200f / (y2 - y1 + 1);
        m.setScale(scaleW, scaleH);

        return Bitmap.createBitmap(
                src, x1, y1, (x2 - x1 + 1), (y2 - y1 + 1), m, true
        );
    }

    /** Lee orientación EXIF de una imagen por Uri. */
    public static int getOrientation(Context context, Uri photoUri) {
        Cursor cursor = context.getContentResolver().query(
                photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION},
                null, null, null
        );
        if (cursor == null) return -1;
        try {
            if (cursor.getCount() != 1) return -1;
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    /** Devuelve la imagen con la orientación corregida según EXIF. */
    public static Bitmap getCorrectlyOrientedImage(Context context, Uri photoUri) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(photoUri);
        BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, dbo);
        if (is != null) is.close();

        int orientation = getOrientation(context, photoUri);

        is = context.getContentResolver().openInputStream(photoUri);
        Bitmap srcBitmap = BitmapFactory.decodeStream(is);
        if (is != null) is.close();

        if (orientation > 0 && srcBitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            srcBitmap = Bitmap.createBitmap(
                    srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true
            );
        }
        return srcBitmap;
    }
}
