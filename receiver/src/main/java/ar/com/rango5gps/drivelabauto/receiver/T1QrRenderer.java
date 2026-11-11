package ar.com.rango5gps.drivelabauto.receiver;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** On-device QR only. No remote QR-image websites and no credentials in URLs. */
final class T1QrRenderer {
    private T1QrRenderer(){}
    static Bitmap bitmap(String url)throws Exception{
        if(url==null||!url.startsWith(
                "https://wgqbvbzhdnkkrpkrohej.supabase.co/functions/v1/drivelab-auto-diagnostics?claim="))
            throw new IllegalArgumentException("QR de nube inválido");
        return render(url);
    }
    static Bitmap pairing(String payload)throws Exception{
        if(payload==null||!payload.matches(
                "drivelabauto://pair\\?ip=[0-9.]{7,15}&ticket=[0-9a-f]{48}"))
            throw new IllegalArgumentException("QR local inválido");
        return render(payload);
    }
    private static Bitmap render(String url)throws Exception{
        final int size=420;
        BitMatrix b=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size);
        int[] pixels=new int[size*size];
        for(int y=0;y<size;y++)
            for(int x=0;x<size;x++)
                pixels[y*size+x]=b.get(x,y)?Color.BLACK:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
}
