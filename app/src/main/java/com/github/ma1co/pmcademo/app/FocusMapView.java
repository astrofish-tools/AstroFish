package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Small overview indicating which part of the sensor preview is magnified. */
final class FocusMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int x, y, factor;
    FocusMapView(Context context) { super(context); setVisibility(GONE); }
    void update(int factor, int x, int y) {
        this.factor=factor; this.x=x; this.y=y;
        setVisibility(factor > 100 ? VISIBLE : GONE); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (factor <= 100) return;
        float left=8, top=8, width=getWidth()-16, height=getHeight()-16;
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xB0000000);
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(155,80,65));
        canvas.drawRect(left,top,left+width,top+height,paint);
        float cx=left+width*(x+1000)/2000f, cy=top+height*(y+1000)/2000f;
        float halfWidth=width*50/factor, halfHeight=height*50/factor;
        paint.setStrokeWidth(2); paint.setColor(Color.rgb(240,130,85));
        canvas.drawRect(cx-halfWidth,cy-halfHeight,cx+halfWidth,cy+halfHeight,paint);
    }
}
