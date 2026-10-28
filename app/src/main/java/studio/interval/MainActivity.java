package studio.interval;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.content.*;
import android.app.AlertDialog;
import android.widget.Toast;
import java.io.*;

public final class MainActivity extends Activity {
    private GameView game;
    private MenuView menu;
    private boolean importing=false;
    private int libraryRequest=0;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);immersive();menu=new MenuView(this);setContentView(menu);refreshLibrary(getPreferences(0).getString("chart",""));}
    private void load(File chart){releaseGame();game=new GameView(this,chart);setContentView(game);}
    private void releaseGame(){if(game!=null){game.dispose();game=null;}}
    public void showTitle(){releaseGame();menu.showTitle();setContentView(menu);}
    public void showSongs(){releaseGame();menu.showSongs();setContentView(menu);}
    public void returnToMenu(){releaseGame();setContentView(menu);menu.invalidate();}
    public void rememberSong(String id){getPreferences(0).edit().putString("chart",id).apply();}
    public void playSong(File chart,boolean auto){rememberSong(chart==null?"":chart.getAbsolutePath());load(chart);game.launch(auto);}
    public void showUtility(boolean settings){load(null);game.showUtility(settings);}
    public void selectDemo(){rememberSong("");refreshLibrary("");showSongs();}
    private void refreshLibrary(String preferred){
        int request=++libraryRequest;
        new Thread(()->{SongLibrary library=SongLibrary.scan(this);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&request==libraryRequest)menu.setLibrary(library,preferred);});},"song-library").start();
    }
    public void importChart(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,91);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=91||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(importing)return;importing=true;menu.setImporting(true);Toast.makeText(this,"正在导入谱面包…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try(InputStream stream=getContentResolver().openInputStream(data.getData())){
                File chart=ChartPackage.importFile(this,stream);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;importing=false;menu.setImporting(false);rememberSong(chart.getAbsolutePath());refreshLibrary(chart.getAbsolutePath());showSongs();Toast.makeText(this,"已加入曲库",Toast.LENGTH_LONG).show();});
            }catch(Exception ex){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){importing=false;menu.setImporting(false);new AlertDialog.Builder(this).setTitle("导入失败").setMessage("请选择电脑制谱器导出的 .interval 文件。\n"+ex.getMessage()).setPositiveButton("确定",null).show();}});}
        },"chart-import").start();
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();if(game!=null)game.setHostActive(focus);}
    @Override protected void onPause(){if(game!=null)game.setHostActive(false);super.onPause();}
    @Override protected void onDestroy(){if(game!=null)game.dispose();super.onDestroy();}
    @Override public void onBackPressed(){if(game!=null)game.back();else if(menu.isSongsPage())showTitle();else super.onBackPressed();}
}
