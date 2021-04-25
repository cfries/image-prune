package com.christianfries.imageprune;

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

				double level = getImageDifference(reference, image, true);
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

	private static double getImageDifference2(final BufferedImage reference, final BufferedImage image, boolean blackWhite) throws IOException {

		if(reference == null) return Double.MAX_VALUE;

		int width = reference.getWidth() / 4;
		int height = reference.getHeight() / 4;

		final BufferedImage referenceScaled = resizeImage(reference, width, height);
		final BufferedImage imageScaled = resizeImage(image, width, height);

		double meanReference = getImageMean(referenceScaled);
		double meanImage = getImageMean(imageScaled);

		double covarSum = 0;
		double varSumReference = 0;
		double varSumImage = 0;
		for(int y =0; y < referenceScaled.getHeight(); y++) {
			for (int x = 0; x < referenceScaled.getWidth(); x++) {
				//Retrieving contents of a pixel
				int pixel1 = referenceScaled.getRGB(x,y);
				int pixel2 = imageScaled.getRGB(x,y);
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

				double diff1 = (double)(red1+green1+blue1)/(3.0*255.0)-meanReference;
				double diff2 = (double)(red2+green2+blue2)/(3.0*255.0)-meanImage;

				covarSum += diff1*diff2;
				varSumReference += diff1*diff1;
				varSumImage += diff2*diff2;
			}
		}

		double level = covarSum / Math.sqrt(varSumReference*varSumImage);

		return (1.0 - level) / 2.0;
	}

	private static double getImageDifference(final BufferedImage reference, final BufferedImage image, boolean blackWhite) throws IOException {

		if(reference == null) return Double.MAX_VALUE;

		boolean isImageHasAlpha = reference.getAlphaRaster() != null;

		byte[] pixelsReference = ((DataBufferByte) reference.getRaster().getDataBuffer()).getData();
		byte[] pixelsImage = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		double meanReference = getImageMean(pixelsReference);
		double meanImage = getImageMean(pixelsImage);

		//		double varReference = getImageVar(pixelsReference);

		double covarSum = 0;
		double varSumReference = 0;
		double varSumImage = 0;

		for(int i=0; i < pixelsReference.length/3; i++) {

			int red1 = Byte.toUnsignedInt(pixelsReference[3*i+0]);
			int green1 = Byte.toUnsignedInt(pixelsReference[3*i+1]);
			int blue1 = Byte.toUnsignedInt(pixelsReference[3*i+2]);

			int red2 = Byte.toUnsignedInt(pixelsImage[3*i+0]);
			int green2 = Byte.toUnsignedInt(pixelsImage[3*i+1]);
			int blue2 = Byte.toUnsignedInt(pixelsImage[3*i+2]);

			double diff1 = (double)(red1+green1+blue1)/(3.0*255.0)-meanReference;
			double diff2 = (double)(red2+green2+blue2)/(3.0*255.0)-meanImage;

			covarSum += diff1*diff2;
			varSumReference += diff1*diff1;
			varSumImage += diff2*diff2;
		}

		double level = covarSum / Math.sqrt(varSumReference*varSumImage);

		return (1.0 - level) / 2.0;
	}

	private static double getImageMean(final byte[] pixels) {
		// IntStream sum does not work if we have more than 2 Megapixels
		return IntStream.range(0, pixels.length).parallel().mapToLong(i -> Byte.toUnsignedInt(pixels[i])).sum() / 255.0 / pixels.length;
		//		return IntStream.range(0, pixels.length).parallel().mapToDouble(i -> (double)Byte.toUnsignedInt(pixels[i]) / 255.0).average().orElse(Double.NaN);
	}

	private static double getImageMean(BufferedImage image) {
		return LongStream.range(0, image.getHeight()).parallel().mapToDouble(i -> {
			double sumForRow = 0;
			int y = (int)i;
			for (int x = 0; x < image.getWidth(); x++) {
				//Retrieving contents of a pixel
				int pixel = image.getRGB(x,y);
				//Creating a Color object from pixel value
				Color color = new Color(pixel, true);
				//Retrieving the R G B values
				int red = color.getRed();
				int green = color.getGreen();
				int blue = color.getBlue();

				sumForRow += (double)(red+green+blue) / 255.0 / 3.0;
			}
			return sumForRow;
		}).sum() / (image.getHeight() * image.getWidth());
	}

	private static double getImageSigma(BufferedImage image, double mean) {
		return Math.sqrt(LongStream.range(0, image.getHeight()).parallel().mapToDouble(i -> {
			double sumOfSquaresForRow = 0;
			int y = (int)i;
			for (int x = 0; x < image.getWidth(); x++) {
				//Retrieving contents of a pixel
				int pixel = image.getRGB(x,y);
				//Creating a Color object from pixel value
				Color color = new Color(pixel, true);
				//Retrieving the R G B values
				int red = color.getRed();
				int green = color.getGreen();
				int blue = color.getBlue();

				sumOfSquaresForRow += Math.pow((red+green+blue)/255.0/3.0 - mean,2);
			}
			return sumOfSquaresForRow;
		}).sum() / (image.getHeight() * image.getWidth()));
	}

	private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
}
