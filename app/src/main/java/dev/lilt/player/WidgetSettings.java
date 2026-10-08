package dev.lilt.player;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
@androidx.media3.common.util.UnstableApi
public final class WidgetSettings extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);setResult(RESULT_CANCELED);int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);if(id==AppWidgetManager.INVALID_APPWIDGET_ID||AppWidgetManager.getInstance(this).getAppWidgetInfo(id)==null||!getPackageName().equals(AppWidgetManager.getInstance(this).getAppWidgetInfo(id).provider.getPackageName())){finish();return;}
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);TextView title=new TextView(this);title.setText("Customize AURA widget");title.setTextSize(24);form.addView(title);RadioGroup themes=new RadioGroup(this);String[] names={"Dark","Light","Gold","Artwork"};int[] themeIds={R.id.theme_dark,R.id.theme_light,R.id.theme_gold,android.R.id.custom};for(int i=0;i<names.length;i++){RadioButton r=new RadioButton(this);r.setId(themeIds[i]);r.setText(names[i]);themes.addView(r);}themes.check(themeIds[Math.max(0,Math.min(3,PlayerWidget.prefs(this).getInt("theme:"+id,3)))]);form.addView(themes);Button save=new Button(this);save.setText("Save widget");form.addView(save);save.setOnClickListener(v->{PlayerWidget.prefs(this).edit().putInt("theme:"+id,themes.getCheckedRadioButtonId()==android.R.id.custom?3:themes.getCheckedRadioButtonId()==R.id.theme_light?1:themes.getCheckedRadioButtonId()==R.id.theme_gold?2:0).apply();PlayerWidget.update(this,AppWidgetManager.getInstance(this),id);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish();});setContentView(form);
    }
}
