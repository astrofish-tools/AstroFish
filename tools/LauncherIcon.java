import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class LauncherIcon {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(96,96,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14,22,38)); g.fillRoundRect(0,0,96,96,20,20);
        g.setColor(new Color(192,119,81)); g.setStroke(new BasicStroke(3)); g.drawOval(18,18,60,60);
        Polygon star=new Polygon();
        for(int i=0;i<8;i++) {
            double a=-Math.PI/2+i*Math.PI/4; double r=i%2==0?23:6;
            star.addPoint(48+(int)Math.round(Math.cos(a)*r),48+(int)Math.round(Math.sin(a)*r));
        }
        g.setColor(new Color(245,218,169)); g.fillPolygon(star);
        g.fillOval(72,12,4,4); g.fillOval(12,70,3,3); g.dispose();
        ImageIO.write(image,"png",new File(args[0]));
    }
}
