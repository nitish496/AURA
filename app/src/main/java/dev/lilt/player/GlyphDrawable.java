package dev.lilt.player;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small original vector icons drawn in a 24-unit coordinate system. */
public final class GlyphDrawable extends Drawable {
    private final String name;private final int color,size;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public GlyphDrawable(String name,int color,int size){this.name=name;this.color=color;this.size=size;}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas) {
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
        switch(name) {
            case "play":paint.setStyle(Paint.Style.FILL);triangle(canvas,8,5,19,12,8,19);break;
            case "pause":paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6,5,10,19,1,1,paint);canvas.drawRoundRect(14,5,18,19,1,1,paint);break;
            case "next":paint.setStyle(Paint.Style.FILL);triangle(canvas,5,5,16,12,5,19);canvas.drawRect(18,5,20,19,paint);break;
            case "previous":paint.setStyle(Paint.Style.FILL);triangle(canvas,19,5,8,12,19,19);canvas.drawRect(4,5,6,19,paint);break;
            case "more":paint.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)canvas.drawCircle(x,12,1.7f,paint);break;
            case "down":line(canvas,5,9,12,16);line(canvas,12,16,19,9);break;
            case "shuffle":line(canvas,4,6,7,6);line(canvas,7,6,17,18);line(canvas,17,18,21,18);line(canvas,4,18,7,18);line(canvas,7,18,17,6);line(canvas,17,6,21,6);line(canvas,18,3,21,6);line(canvas,21,6,18,9);line(canvas,18,15,21,18);line(canvas,21,18,18,21);break;
            case "repeat":case "repeat_one":canvas.drawArc(4,5,20,19,185,150,false,paint);canvas.drawArc(4,5,20,19,5,150,false,paint);line(canvas,20,5,20,10);line(canvas,20,10,15,10);line(canvas,4,19,4,14);line(canvas,4,14,9,14);if(name.equals("repeat_one")){paint.setStyle(Paint.Style.FILL);paint.setTextSize(8);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("1",12,15,paint);}break;
            case "refresh":canvas.drawArc(4,4,20,20,45,285,false,paint);line(canvas,20,4,20,10);line(canvas,20,10,14,10);break;
            case "home":line(canvas,3,11,12,3);line(canvas,12,3,21,11);line(canvas,5,9,5,21);line(canvas,5,21,19,21);line(canvas,19,21,19,9);line(canvas,10,21,10,15);line(canvas,10,15,14,15);line(canvas,14,15,14,21);break;
            case "library":canvas.drawRoundRect(4,5,9,19,1,1,paint);canvas.drawRoundRect(12,5,17,19,1,1,paint);line(canvas,19,6,21,18);break;
            case "search":canvas.drawCircle(10,10,6,paint);line(canvas,15,15,21,21);break;
            case "playlists":line(canvas,4,6,19,6);line(canvas,4,11,14,11);line(canvas,4,16,12,16);line(canvas,18,11,18,19);paint.setStyle(Paint.Style.FILL);canvas.drawOval(14,17,19,21,paint);break;
            default:canvas.drawCircle(12,12,7,paint);
        }
        canvas.restore();
    }
    private void triangle(Canvas c,float a,float b,float d,float e,float f,float g){Path p=new Path();p.moveTo(a,b);p.lineTo(d,e);p.lineTo(f,g);p.close();c.drawPath(p,paint);}
    private void line(Canvas c,float a,float b,float d,float e){c.drawLine(a,b,d,e,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    public static String name(String text){switch(text){case "▶":return "play";case "Ⅱ":return "pause";case "›":case "▶▶":return "next";case "◀◀":return "previous";case "•••":return "more";case "⌄":return "down";case "↻":return "refresh";case "shuffle":return "shuffle";case "repeat":return "repeat";case "repeat_one":return "repeat_one";default:return null;}}
}
