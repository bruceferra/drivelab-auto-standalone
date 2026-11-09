package ar.com.rango5gps.drivelabauto.common;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Only the one previously verified APK is available, read-only, to the native
 * Android package installer. No arbitrary paths, user files or write access.
 * This stays separate from DriveLab T1 telemetry and DriveLab Uber Beta.
 */
public final class UpdateApkProvider extends ContentProvider {
    public static final String NAME="drivelab-native-update.apk";
    public static File apk(Context context){
        return new File(new File(context.getCacheDir(),"updates"),NAME);
    }
    public static Uri uri(Context context){
        return Uri.parse("content://"+context.getPackageName()+".updatefiles/apk");
    }
    private File checked(Uri u)throws FileNotFoundException {
        if(u==null||!"content".equals(u.getScheme())||
           !"/apk".equals(u.getPath())||u.getQuery()!=null||
           !(getContext().getPackageName()+".updatefiles").equals(u.getAuthority()))
            throw new FileNotFoundException("Invalid installer file path");
        File file=apk(getContext());
        if(!file.isFile()||file.length()<1024||file.length()>50L*1024*1024)
            throw new FileNotFoundException("Verified APK not present");
        return file;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri u){
        try{checked(u);return "application/vnd.android.package-archive";}
        catch(FileNotFoundException error){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)
            throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only provider");
        return ParcelFileDescriptor.open(checked(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
            String[] selectionArgs,String sortOrder){
        try{
            File file=checked(uri);
            String[] cols=projection==null?
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=NAME;
                else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file.length();
            }
            MatrixCursor cursor=new MatrixCursor(cols);
            cursor.addRow(row);return cursor;
        }catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int update(Uri uri,ContentValues values,String where,String[] selectionArgs){
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int delete(Uri uri,String where,String[] args){
        throw new UnsupportedOperationException("Read-only provider");
    }
}
