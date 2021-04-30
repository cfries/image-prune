package com.christianfries.surveillancecamera;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.imageio.ImageIO;

public class ImagePrune {


	public static void main(String[] args) throws IOException, InterruptedException {
		if(args.length != 2) throw new IllegalArgumentException("Please provide path to images and threshold as arguments.");
		String directory = args[0];
		Double threshold = Double.valueOf(args[1]);

		File directoryPath = new File(directory);
		// List of all files and directories
		String contents[] = directoryPath.list();
		Arrays.sort(contents);

		ExecutorService fileDeletionService = Executors.newSingleThreadExecutor();

		BufferedImage reference = null;
		for(int i=0; i<contents.length; i++) {

			try {
				// Load the images
				BufferedImage image = ImageIO.read(new File(directory + File.separator + contents[i]));

				double level = new ImageCompare().getImageDifference(reference, image);
				reference = image;

				System.out.print(contents[i] + "\t" + i + "\t" + level);
				if(level < threshold) {
					System.out.println(" will be deleted.");

					final String filename = contents[i];
					final String pathname = directory + File.separator + contents[i];
					fileDeletionService.submit(() -> {
						File f= new File(pathname);           //file to be delete  
						if(!f.delete()) {  
							System.out.println(filename + " deletion failed.");
						}
					});
				}
				else {
					System.out.println(" ok");
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		fileDeletionService.shutdown();
		fileDeletionService.awaitTermination(10, TimeUnit.SECONDS);
	}
}
