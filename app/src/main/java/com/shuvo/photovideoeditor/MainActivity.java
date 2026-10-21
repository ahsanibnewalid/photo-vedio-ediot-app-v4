package com.shuvo.photovideoeditor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
  private LinearLayout root;
  private ImageView preview;
  private Bitmap bitmap;
  private Uri videoUri;
  private VideoView video;
  private SeekBar start,end;
  private int duration;
  private TextView status;
  private static final int PHOTO=1, VIDEO=2;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    root=new LinearLayout(this); root.setOrientation(1);root.setPadding(18,20,18,12);
    root.setBackgroundColor(Color.rgb(22,27,40));setContentView(root);
    TextView title=new TextView(this);title.setText("SHUVO STUDIO");title.setTextSize(26);title.setTextColor(Color.WHITE);root.addView(title);
    addButton("Open Photo",()->pick("image/*",PHOTO));
    addButton("Open Video",()->pick("video/*",VIDEO));
    status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("Select a photo or video");root.addView(status);
  }
  private void addButton(String name,Runnable action) { Button b=new Button(this);b.setText(name);root.addView(b);b.setOnClickListener(v->action.run());}
  private void pick(String mime,int code) { Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code); }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    try { if(request==PHOTO)openPhoto(data.getData());else if(request==VIDEO)openVideo(data.getData()); }
    catch(Exception e){status.setText("Error: "+e.getMessage());}
  }
  private void clearEditor() {while(root.getChildCount()>4)root.removeViewAt(4);}
  private void openPhoto(Uri uri)throws Exception {
    try(InputStream in=getContentResolver().openInputStream(uri)){
      BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;
      Bitmap decoded=BitmapFactory.decodeStream(in,null,opt);
      if(decoded==null)throw new IOException("Unsupported image");
      bitmap=decoded.copy(Bitmap.Config.ARGB_8888,true);
    }
    clearEditor();preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(700);preview.setImageBitmap(bitmap);root.addView(preview);
    addButton("Rotate 90°",()->{Matrix m=new Matrix();m.postRotate(90);bitmap=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);preview.setImageBitmap(bitmap);});
    addButton("Grayscale Filter",()->{
      Bitmap out=bitmap.copy(Bitmap.Config.ARGB_8888,true);Canvas c=new Canvas(out);Paint p=new Paint();
      ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);p.setColorFilter(new ColorMatrixColorFilter(matrix));c.drawBitmap(bitmap,0,0,p);bitmap=out;preview.setImageBitmap(bitmap);
    });
    EditText text=new EditText(this);text.setHint("Text on photo");text.setTextColor(Color.WHITE);root.addView(text);
    addButton("Add Text",()->{
      Canvas c=new Canvas(bitmap);Paint p=new Paint(3);p.setColor(Color.WHITE);p.setTextSize(Math.max(32,bitmap.getWidth()/14f));c.drawText(text.getText().toString(),20,bitmap.getHeight()/2f,p);preview.invalidate();preview.setImageBitmap(bitmap);
    });
    addButton("Save Photo",()->savePhoto());
    status.setText("Photo loaded");
  }
  private void savePhoto(){
    Uri dest=null;
    try {
      ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Shuvo_"+System.currentTimeMillis()+".png");
      values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ShuvoStudio");
      dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
      if(dest==null)throw new IOException("Cannot save photo");
      try(OutputStream out=getContentResolver().openOutputStream(dest)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Write failed");}
      status.setText("Photo saved to Pictures/ShuvoStudio");
    }catch(Exception e){if(dest!=null)getContentResolver().delete(dest,null,null);status.setText("Save failed: "+e.getMessage());}
  }
  private void openVideo(Uri uri){
    videoUri=uri;clearEditor();video=new VideoView(this);video.setVideoURI(uri);root.addView(video,new LinearLayout.LayoutParams(-1,450));
    addButton("Play / Pause",()->{if(video.isPlaying())video.pause();else video.start();});
    start=new SeekBar(this);end=new SeekBar(this);
    root.addView(start);root.addView(end);
    video.setOnPreparedListener(mp->{duration=mp.getDuration();int secs=Math.max(1,duration/1000);start.setMax(secs);end.setMax(secs);end.setProgress(secs);});
    addButton("Export Trimmed Video",()->exportVideo());
    status.setText("Choose trim start and end");
  }
  private void exportVideo(){
    if(videoUri==null||end.getProgress()<=start.getProgress()){status.setText("Invalid trim range");return;}
    status.setText("Exporting...");
    new Thread(()->{
      Uri output=null;
      try {
        ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"Shuvo_"+System.currentTimeMillis()+".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/ShuvoStudio");values.put(MediaStore.Video.Media.IS_PENDING,1);
        output=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
        if(output==null)throw new IOException("Cannot create video");
        trim(videoUri,output,start.getProgress()*1000000L,end.getProgress()*1000000L);
        values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(output,values,null,null);
        runOnUiThread(()->status.setText("Video saved to Movies/ShuvoStudio"));
      }catch(Exception ex){if(output!=null)getContentResolver().delete(output,null,null);runOnUiThread(()->status.setText("Export failed: "+ex.getMessage()));}
    }).start();
  }
  private void trim(Uri source,Uri dest,long from,long to)throws Exception{
    android.media.MediaExtractor extractor=new android.media.MediaExtractor();
    android.media.MediaMuxer muxer=null;
    try{
      extractor.setDataSource(this,source,null);
      try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(dest,"w")){
        if(fd==null)throw new IOException("Output unavailable");
        muxer=new android.media.MediaMuxer(fd.getFileDescriptor(),android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int[] mapping=new int[extractor.getTrackCount()];java.util.Arrays.fill(mapping,-1);
        for(int i=0;i<mapping.length;i++){
          android.media.MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(android.media.MediaFormat.KEY_MIME);
          if(mime!=null&&(mime.startsWith("audio/")||mime.startsWith("video/"))){mapping[i]=muxer.addTrack(format);extractor.selectTrack(i);}
        }
        muxer.start();extractor.seekTo(from,android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocateDirect(4*1024*1024);
        android.media.MediaCodec.BufferInfo info=new android.media.MediaCodec.BufferInfo();long base=-1;
        while(extractor.getSampleTrackIndex()>=0){
          int track=extractor.getSampleTrackIndex();long time=extractor.getSampleTime();
          if(time>=to)break;
          buffer.clear();int size=extractor.readSampleData(buffer,0);if(size<0)break;
          if(mapping[track]>=0){if(base<0)base=time;info.offset=0;info.size=size;info.presentationTimeUs=Math.max(0,time-base);info.flags=extractor.getSampleFlags();muxer.writeSampleData(mapping[track],buffer,info);}
          extractor.advance();
        }
        muxer.stop();
      }
    }finally{extractor.release();if(muxer!=null)muxer.release();}
  }
}