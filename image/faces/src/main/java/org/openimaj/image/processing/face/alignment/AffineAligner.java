package org.openimaj.image.processing.face.alignment;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.openimaj.image.DisplayUtilities;
import org.openimaj.image.FImage;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.processing.face.parts.KEDetectedFace;
import org.openimaj.image.processing.face.parts.FacialKeypoint;
import org.openimaj.image.processing.face.parts.FKEFaceDetector;

import Jama.Matrix;

public class AffineAligner implements FaceAligner<KEDetectedFace> {
	/**
	 * Normalised positions of facial parts
	 */
	protected final static float [][] Pmu = {{25.0347f, 34.1802f, 44.1943f, 53.4623f, 34.1208f, 39.3564f, 44.9156f, 31.1454f, 47.8747f},
			  					   			 {34.1580f, 34.1659f, 34.0936f, 33.8063f, 45.4179f, 47.0043f, 45.3628f, 53.0275f, 52.7999f}};
	
	private static final FImage DEFAULT_MASK = loadDefaultMask();
	
	final static int CANONICAL_SIZE = 80;

	int facePatchSize = 100;
	float facePatchBorderPercentage = 0.225f;
	
	private FImage mask = DEFAULT_MASK;
	
	public AffineAligner() {};
	
	public AffineAligner(FImage mask) {
		this.mask = mask;
	}
	
	@Override
	public FImage align(KEDetectedFace descriptor) {
		double size = facePatchSize + 2.0 * facePatchSize * facePatchBorderPercentage;
		double sc = (double)CANONICAL_SIZE / size;
		
		//do the scaling to everything but the translation!!!
		Matrix T = estimateAffineTransform(descriptor);
		T.set(0, 0, T.get(0, 0) * sc);
		T.set(1, 1, T.get(1, 1) * sc);
		T.set(0, 1, T.get(0, 1) * sc);
		T.set(1, 0, T.get(1, 0) * sc);
		
		FImage J = FKEFaceDetector.pyramidResize(descriptor.getFacePatch(), T);	
		return FKEFaceDetector.extractPatch(J, T, (int) size, (int) (size*facePatchBorderPercentage));
	}
	
	/**
	 * Estimate the affine transform required to warp a set of
	 * facial keypoints to their canonical coordinates.
	 * 
	 * Affine transform is from a flat, vertically oriented (canonical) face to 
	 * located face space. You'll need to invert this if you want to 
	 * use it to extract the face from the image.
	 *
	 * @param face the face
	 * @return the affine transform matrix
	 */
	public static Matrix estimateAffineTransform(KEDetectedFace face) {
		return estimateAffineTransform(face.getKeypoints());
	}
	
	protected static Matrix estimateAffineTransform(FacialKeypoint[] pts) {
		float emin=Float.POSITIVE_INFINITY;
		Matrix T = null;

		for (int c=0; c<9; c++) {
			Matrix A = new Matrix(8,3);
			Matrix B = new Matrix(8,3);
			for (int i=0, j=0; i<9; i++) {
				if (i!=8-c) {
					A.set(j, 0, Pmu[0][i]);
					A.set(j, 1, Pmu[1][i]);
					A.set(j, 2, 1);
					B.set(j, 0, pts[i].position.x);
					B.set(j, 1, pts[i].position.y);
					B.set(j, 2, 1);
					j++;
				}
			}

			Matrix Tc = A.solve(B).transpose();

			Matrix P1 = Tc.times(A.transpose());
			Matrix D = P1.minus(B.transpose());

			float e = 0;
			for (int cc=0; cc<D.getColumnDimension(); cc++) {
				float colsum = 0;
				for (int rr=0; rr<D.getRowDimension(); rr++) {
					colsum += D.get(rr, cc) * D.get(rr, cc);;
				}
				e += Math.sqrt(colsum);
			}

			if (e<emin) {
				emin=e;
				T=Tc;
			}
		}

		return T;
	}
	
	private static FImage loadDefaultMask() {
		try {
			return ImageUtilities.readF(FaceAligner.class.getResourceAsStream("affineMask.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public FImage getMask() {
		return mask;
	}

	public static void main(String [] args) throws Exception {
		FImage image1 = ImageUtilities.readF(new File("/Volumes/Raid/face_databases/faces/image_0001.jpg"));
		List<KEDetectedFace> faces = new FKEFaceDetector().detectFaces(image1);
		
		AffineAligner warp = new AffineAligner();
		DisplayUtilities.display(warp.align(faces.get(0)));
		DisplayUtilities.display(warp.getMask());
	}
}
