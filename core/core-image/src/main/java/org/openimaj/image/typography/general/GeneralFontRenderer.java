/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * 
 */
package org.openimaj.image.typography.general;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.List;

import org.openimaj.image.renderer.ImageRenderer;
import org.openimaj.image.typography.FontRenderer;
import org.openimaj.math.geometry.point.Point2dImpl;
import org.openimaj.math.geometry.shape.Polygon;
import org.openimaj.math.geometry.shape.Rectangle;

import com.caffeineowl.graphics.bezier.BezierUtils;
import com.caffeineowl.graphics.bezier.CubicSegmentConsumer;
import com.caffeineowl.graphics.bezier.QuadSegmentConsumer;
import com.caffeineowl.graphics.bezier.flatnessalgos.SimpleConvexHullSubdivCriterion;

/**
 *	A font renderer that takes the glyph outline as generated by the
 *	Java AWT Font system and renders it into an OpenIMAJ image using the
 *	ImageRenderer methods.
 *
 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *  @created 18 Aug 2011
 *	
 *
 *	@param <T> The image pixel type
 */
public class GeneralFontRenderer<T> extends FontRenderer<T,GeneralFontStyle<T>>
{
	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.image.typography.FontRenderer#renderText(org.openimaj.image.renderer.ImageRenderer, java.lang.String, int, int, org.openimaj.image.typography.FontStyle)
	 */
	@Override
	public void renderText( final ImageRenderer<T, ?> renderer, final String text,
			final int x, final int y, final GeneralFontStyle<T> style )
	{
		final List<Polygon> p = this.getPolygons( text, x, y, style );

		if( style.isOutline() )
		{
			for( final Polygon polyOuter : p )
			{
				if( polyOuter.nVertices() > 0 )
					renderer.drawPolygon( polyOuter, style.getColour() );
				for( final Polygon poly : polyOuter.getInnerPolys() )
					if( poly.nVertices() > 0 )
						renderer.drawPolygon( poly, style.getColour() );
			}
		}
		else
		{
			for( final Polygon poly : p )
				if( poly.nVertices() > 0 )
					renderer.drawPolygonFilled( poly, style.getColour() );
		}
	}

	/**
	 * 	Returns a list of polygons that represent the letters in the given
	 * 	text. If the font style is outline, the holes will be delivered as
	 * 	separate polygons otherwise they will be integrated into the letter
	 * 	polygons.
	 * 
	 *	@param text The text to render as a polygon
	 *	@param x The x-coordinate
	 *	@param y The y-coordinate
	 *	@param style The font's style
	 *	@return A list of polygons
	 */
	public List<Polygon> getPolygons( final String text, final int x, final int y, final GeneralFontStyle<T> style )
	{
		final List<Polygon> p = new ArrayList<Polygon>();
		int spaceOffset = 0;
		int xx = x;
		for( final char c : text.toCharArray() )
		{
			System.out.println( "Char: '"+c+"' : spaceOffset: "+spaceOffset);
			if( c == ' ' )
				spaceOffset += style.getFontSize()/2;
			else
			{
				if( c == '\t' )
					spaceOffset += style.getFontSize()*4;
				else
				{
					final Polygon pp = this.getPolygon( c, xx + spaceOffset, y, style );
					p.add( pp );
					xx = (int)pp.maxX();
					spaceOffset = 0;
				}
			}
		}

		return p;
	}

	/**
	 * 	Returns a polygon that represent the given character.
	 *  If the font style is outline, the holes will be delivered as
	 * 	separate polygons otherwise they will be integrated into the letter
	 * 	polygons.
	 * 
	 *	@param character The character to render as a polygon
	 *	@param x The x-coordinate
	 *	@param y The y-coordinate
	 *	@param style The font's style
	 *	@return A polygon
	 */
	public Polygon getPolygon( final char character,
			final int x, final int y, final GeneralFontStyle<T> style )
	{
		final Font f = new Font(
				style.getFont().getName(),
				style.getFont().getType(),
				style.getFontSize() );

		final FontRenderContext frc = new FontRenderContext(
				new AffineTransform(), true, true );
		final GlyphVector g = f.createGlyphVector( frc, new char[]{character} );

		final Polygon letterPoly = new Polygon();
		Polygon currentPoly = null;
		for( int i = 0; i < g.getNumGlyphs(); i++ )
		{
			final GeneralPath s = (GeneralPath)g.getGlyphOutline( i, x, y );
			final PathIterator pi = s.getPathIterator( new AffineTransform() );

			final float[] ps = new float[6];
			float xx = 0, yy = 0;
			while( !pi.isDone() )
			{
				final int t = pi.currentSegment( ps );

				switch( t )
				{
				case PathIterator.SEG_MOVETO:
				{
					if( currentPoly != null && currentPoly.nVertices() > 0 )
						letterPoly.addInnerPolygon(
								currentPoly.roundVertices() );
					currentPoly = new Polygon();

					//						if( letterPoly != null && letterPoly.nVertices() > 0 &&
					//							letterPoly.isInside( new Point2dImpl( ps[0], ps[1] ) ) )
					//							currentPoly.setIsHole( true );

					currentPoly.addVertex( ps[0], ps[1] );
					xx = ps[0]; yy = ps[1];
					break;
				}
				case PathIterator.SEG_LINETO:
				{
					currentPoly.addVertex( ps[0], ps[1] );
					xx = ps[0]; yy = ps[1];
					break;
				}
				case PathIterator.SEG_QUADTO:
				{
					final QuadCurve2D c = new QuadCurve2D.Double(
							xx, yy, ps[0], ps[1], ps[2], ps[3] );
					final Polygon p = currentPoly;
					BezierUtils.adaptiveHalving( c , new SimpleConvexHullSubdivCriterion(),
							new QuadSegmentConsumer()
					{
						@Override
						public void processSegment( final QuadCurve2D segment, final double startT, final double endT )
						{
							if( 0.0 == startT )
								p.addVertex( new Point2dImpl(
										(float)segment.getX1(), (float)segment.getY1() ) );

							p.addVertex( new Point2dImpl(
									(float)segment.getX2(), (float)segment.getY2() ) );
						}
					}
							);
					xx = ps[2]; yy = ps[3];
					break;
				}
				case PathIterator.SEG_CUBICTO:
				{
					final CubicCurve2D c = new CubicCurve2D.Double(
							xx, yy, ps[0], ps[1],
							ps[2], ps[3], ps[4], ps[5] );
					final Polygon p = currentPoly;
					BezierUtils.adaptiveHalving( c , new SimpleConvexHullSubdivCriterion(),
							new CubicSegmentConsumer()
					{
						@Override
						public void processSegment( final CubicCurve2D segment,
								final double startT, final double endT )
						{
							if( 0.0 == startT )
								p.addVertex( new Point2dImpl(
										(float)segment.getX1(), (float)segment.getY1() ) );

							p.addVertex( new Point2dImpl(
									(float)segment.getX2(), (float)segment.getY2() ) );
						}
					}
							);
					xx = ps[4]; yy = ps[5];
					break;
				}
				case PathIterator.SEG_CLOSE:
				{
					currentPoly.addVertex( ps[0], ps[1] );
					letterPoly.addInnerPolygon(
							currentPoly.roundVertices() );
					currentPoly = new Polygon();

					break;
				}
				}

				pi.next();
			}
		}

		// System.out.println( ""+character+": "+letterPoly );
		return letterPoly;
	}

	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.image.typography.FontRenderer#getBounds(java.lang.String, org.openimaj.image.typography.FontStyle)
	 */
	@Override
	public Rectangle getBounds( final String string, final GeneralFontStyle<T> style )
	{
		final Rectangle bounds = new Rectangle(0,0,Float.MIN_VALUE,Float.MIN_VALUE);

		final List<Polygon> polys = this.getPolygons( string, 0, 0, style );
		for( final Polygon p : polys )
		{
			bounds.x = (float) Math.min( bounds.x, p.minX() );
			bounds.y = (float) Math.min( bounds.y, p.minY() );
			bounds.width = (float) Math.max( bounds.width, p.maxX() - bounds.x);
			bounds.height = (float) Math.max( bounds.height, p.maxY() - bounds.y);
		}

		return bounds;
	}
}
