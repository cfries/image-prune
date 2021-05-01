package com.christianfries.surveillancecamera;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ImageCompare {
	
		public double getImageDifference(final BufferedImage reference, final BufferedImage image) throws IOException {

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

			double contrast = 2.0 * DoubleStream.builder().add(meanReference).add(meanImage).add(1.0-meanReference).add(1.0-meanImage).build().min().getAsDouble();

			double correlation = covarSum / Math.sqrt(varSumReference*varSumImage);
			double level = (1+contrast) /2.0  *  (1.0 - correlation) / 2.0;
			
			return level;
		}

		private static double getImageMean(final byte[] pixels) {
			// IntStream sum does not work if we have more than 2 Megapixels
			return IntStream.range(0, pixels.length).parallel().mapToLong(i -> Byte.toUnsignedInt(pixels[i])).sum() / 255.0 / pixels.length;
			//		return IntStream.range(0, pixels.length).parallel().mapToDouble(i -> (double)Byte.toUnsignedInt(pixels[i]) / 255.0).average().orElse(Double.NaN);
		}

		private static double getImageMeanSquared(final byte[] pixels) {
			return IntStream.range(0, pixels.length).parallel().mapToLong(i -> {
				long value = (long) Byte.toUnsignedInt(pixels[i]);
				return value*value;
			}).sum() / 255.0 / pixels.length;
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
			Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_FAST);
			BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
			outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
			return outputImage;
		}
	}