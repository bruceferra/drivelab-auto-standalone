package ar.com.rango5gps.drivelabauto.boot;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Entirely local QR; no credential or pairing token leaves the T1. */
final class BootQr {
    private BootQr(){}
    static Bitmap draw(String payload)throws Exception{
        if(payload==null||
           !payload.matches("drivelabauto://pair\\?ip=[0-9.]{7,15}&ticket=[0-9a-f]{48}"))
            throw new IllegalArgumentException("QR local inválido");
        int size=400;
        BitMatrix bits=new QRCodeWriter().encode(
            payload,BarcodeFormat.QR_CODE,size,size);
        int[] pixels=new int[size*size];
        for(int y=0;y<size;y++)
            for(int x=0;x<size;x++)
                pixels[y*size+x]=bits.get(x,y)?Color.BLACK:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
}
