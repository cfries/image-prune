/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 27.03.2021
 */
package com.christianfries.surveillancecamera;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/**
 * This is a standalone version of the image comparisson operating on a given folder and deleting all images in the sequence that are too similar.
 * 
 * The program requires two command-line options, in this order:
 * 
 * <ul>
 * 	<li> `sourceDir`: the directory to be scanned for images.
 * 	<li> `threshold`: a floating-point number between 0 and 1 determining when a picture is considered to be different. You can see it as a percentage value. A good value is 0.018.
 * </ul> 
 * <b>WARNING:</b> <i>Be aware that the program deletes images that are considered to be duplicates!</i>
 * 
 * @author Christian Fries
 */
public class ImagePrune {

	public static void main(String[] args) throws IOException, InterruptedException {
		if(args.length != 2) {
			throw new IllegalArgumentException("Please provide path to images and threshold as arguments.");
		}
		final String directory = args[0];
		final Double threshold = Double.valueOf(args[1]);

		final File directoryPath = new File(directory);
		// List of all files and directories
		final String contents[] = directoryPath.list();
		Arrays.sort(contents);

		final ExecutorService fileDeletionService = Executors.newSingleThreadExecutor();

		BufferedImage reference = null;
		for(int i=0; i<contents.length; i++) {

			try {
				// Load the images
				final BufferedImage image = ImageIO.read(new File(directory + File.separator + contents[i]));

				final double level = new ImageCompare().getImageDifference(reference, image);
				reference = image;

				System.out.print(contents[i] + "\t" + i + "\t" + level);
				if(level < threshold) {
					System.out.println(" will be deleted.");

					final String filename = contents[i];
					final String pathname = directory + File.separator + contents[i];
					fileDeletionService.submit(() -> {
						final File f= new File(pathname);           //file to be delete
						if(!f.delete()) {
							System.out.println(filename + " deletion failed.");
						}
					});
				}
				else {
					System.out.println(" ok");
				}
			}
			catch(final Exception e) {
				e.printStackTrace();
			}
		}
		fileDeletionService.shutdown();
		fileDeletionService.awaitTermination(10, TimeUnit.SECONDS);
	}
}
