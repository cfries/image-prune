package com.christianfries.imageprune;

import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

public class ImagePrune {

	public static void main(String[] args) throws IOException {
		if(args.length != 2) throw new IllegalArgumentException("Please provide path to images and threshold as arguments.");
		String directory = args[0];
		Double threshold = Double.valueOf(args[1]);

		File directoryPath = new File(directory);
		// List of all files and directories
		String contents[] = directoryPath.list();
		Arrays.sort(contents);

		BufferedImage reference = null;
		for(int i=0; i<contents.length; i++) {

			try {
				// Load the images
				BufferedImage image = ImageIO.read(new File(directory + File.separator + contents[i]));
				if(reference == null) {
					reference = image;
					continue;
				}

				long difference = 0;
				for (int y = 0; y < reference.getHeight(); y++) {
					for (int x = 0; x < reference.getWidth(); x++) {
						//Retrieving contents of a pixel
						int pixel1 = reference.getRGB(x,y);
						int pixel2 = image.getRGB(x,y);
						//Creating a Color object from pixel value
						Color color1 = new Color(pixel1, true);
						//Retrieving the R G B values
						int red1 = color1.getRed();
						int green1 = color1.getGreen();
						int blue1 = color1.getBlue();
						Color color2 = new Color(pixel2, true);
						//Retrieving the R G B values
						int red2 = color2.getRed();
						int green2 = color2.getGreen();
						int blue2 = color2.getBlue();

						difference += Math.abs(red1-red2) + Math.abs(green1-green2) + Math.abs(blue1-blue2);
					}
				}
				reference = image;
				double level = (double)difference / (reference.getHeight()*reference.getWidth()) / (3.0*255);

				System.out.print(contents[i] + "\t" + i + "\t" + level);

				if(level < threshold) {
					File f= new File(directory + File.pathSeparatorChar + contents[i]);           //file to be delete  
					if(f.delete())                      //returns Boolean value  
					{  
						System.out.println(" deleted");   //getting and printing the file name  
					}
				}
				else {
					System.out.println(" ok");
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
}
