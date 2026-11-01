package studio.interval.godot.tests;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only test fixture provider, exercising the same content:// path as Android's picker. */
public final class FixtureProvider extends ContentProvider {
    private File fixture;
    @Override public boolean onCreate() {
        fixture=new File(getContext().getCacheDir(),"flow.interval");
        try(InputStream in=getContext().getAssets().open("flow.interval");OutputStream out=new FileOutputStream(fixture)) {
            byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return true;
        } catch(IOException e) {return false;}
    }
    private void validate(Uri uri)throws FileNotFoundException {if(!"/flow.interval".equals(uri.getPath()))throw new FileNotFoundException();}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        validate(uri);if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(fixture,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return "application/zip";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.SIZE.equals(columns[i])?fixture.length():"flow.interval";
        cursor.addRow(row);return cursor;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
