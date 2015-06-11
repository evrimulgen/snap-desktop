/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.rcp.mask;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.figure.ShapeFigure;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.GeoCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.datamodel.TiePointGrid;
import org.esa.snap.framework.datamodel.TransectProfileData;
import org.esa.snap.framework.datamodel.TransectProfileDataBuilder;
import org.esa.snap.framework.ui.SelectExportMethodDialog;
import org.esa.snap.framework.ui.UIUtils;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.jai.ImageManager;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.StringUtils;
import org.esa.snap.util.SystemUtils;
import org.esa.snap.util.io.FileUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.*;

import javax.swing.*;
import java.awt.Dialog;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;





@ActionID(
        category = "File",
        id = "org.esa.snap.rcp.mask.ExportTransectPixelsAction"
)
@ActionRegistration(
        displayName = "#CTL_ExportTransectPixelsAction_MenuText",
        popupText = "#CTL_ExportTransectPixelsAction_MenuText"
)
@ActionReferences(
        {
                @ActionReference(
                        path = "Menu/File/Export Others",
                        position = 0
                ),
                @ActionReference(
                        path = "Context/Product/RasterDataNode",
                        position = 208
                )
        }
)


@NbBundle.Messages({
        "CTL_ExportTransectPixelsAction_MenuText=Transect Pixels",
        "CTL_ExportTransectPixelsAction_ShortDescription=Export Transect Pixels..."
})

public class ExportTransectPixelsAction extends AbstractAction implements ContextAwareAction,LookupListener,HelpCtx.Provider {

    private static final String DLG_TITLE = "Export Transect Pixels";
    private static final String ERR_MSG_BASE = "Transect pixels cannot be exported:\n";
    private static final String HELP_ID = "exportTransectPixels";


    /**
     * Invoked when a command action is performed.
     *
     * @param event the command event
     */

    @Override
    public void actionPerformed(ActionEvent event) {
        exportTransectPixels();
    }

    /**
     * Called when a command should update its state.
     * <p> This method can contain some code which analyzes the underlying element and makes a decision whether
     * this item or group should be made visible/invisible or enabled/disabled etc.
     *
     * @param event the command event
     */

    public void resultChanged(LookupEvent event) {
        ensureListenerIsRegistered();
        ProductSceneView view = SnapApp.getDefault().getSelectedProductSceneView();
        boolean enabled = view != null && view.getCurrentShapeFigure() != null && view.getCurrentShapeFigure().isSelected();
        setEnabled(enabled);
    }

    private void ensureListenerIsRegistered() {
//        if(!listenerIsRegistered) {
//            SelectionManager selectionManager = getAppContext().getApplicationPage().getSelectionManager();
//            selectionManager.addSelectionChangeListener(this);cd
//            listenerIsRegistered = true;
//        }
    }

    private void exportTransectPixels() {

        // Get current VISAT view showing a product's band
        final ProductSceneView view = SnapApp.getDefault().getSelectedProductSceneView();
        if (view == null) {
            return;
        }
        // Get the displayed raster data node (band or tie-point grid)
        final RasterDataNode raster = view.getRaster();
        // Get the transect of the displayed raster data node
        final ShapeFigure transect = view.getCurrentShapeFigure();
        if (transect == null) {
            SnapDialogs.showError(DLG_TITLE,
                    ERR_MSG_BASE + "There is no transect defined in the selected band.");  /*I18N*/
            return;
        }

        final TransectProfileData transectProfileData;
        try {
            transectProfileData = new TransectProfileDataBuilder()
                    .raster(raster)
                    .path(transect.getShape())
                    .build();
        } catch (IOException e) {
            SnapDialogs.showError(DLG_TITLE,
                    ERR_MSG_BASE + "An I/O error occurred:\n" + e.getMessage());   /*I18N*/
            return;
        }

        // Compute total number of transect pixels
        final int numTransectPixels = getNumTransectPixels(raster.getProduct(), transectProfileData);

        String numPixelsText;
        if (numTransectPixels == 1) {
            numPixelsText = "One transect pixel will be exported.\n"; /*I18N*/
        } else {
            numPixelsText = numTransectPixels + " transect pixels will be exported.\n"; /*I18N*/
        }
        // Get export method from user
        final String questionText = "How do you want to export the pixel values?\n"; /*I18N*/
        final JCheckBox createHeaderBox = new JCheckBox("Create header");
        final JCheckBox exportTiePointsBox = new JCheckBox("Export tie-points");
        final JCheckBox exportWavelengthsAndSFBox = new JCheckBox("Export wavelengths + solar fluxes");
        final int method = SelectExportMethodDialog.run(SnapApp.getDefault().getMainFrame(), getWindowTitle(),
                                                        questionText + numPixelsText, new JCheckBox[]{
                createHeaderBox,
                exportTiePointsBox,
                exportWavelengthsAndSFBox
        }, "exportTransectPixels");

        final PrintWriter out;
        final StringBuffer clipboardText;
        final int initialBufferSize = 256000;
        if (method == SelectExportMethodDialog.EXPORT_TO_CLIPBOARD) {
            // Write into string buffer
            final StringWriter stringWriter = new StringWriter(initialBufferSize);
            out = new PrintWriter(stringWriter);
            clipboardText = stringWriter.getBuffer();
        } else if (method == SelectExportMethodDialog.EXPORT_TO_FILE) {
            // Write into file, get file from user
            final File file = promptForFile( createDefaultFileName(raster));
            if (file == null) {
                return; // Cancel
            }
            final FileWriter fileWriter;
            try {
                fileWriter = new FileWriter(file);
            } catch (IOException e) {
                SnapDialogs.showError(DLG_TITLE,
                                      ERR_MSG_BASE + "Failed to create file '" + file + "':\n" + e.getMessage()); /*I18N*/
                return; // Error
            }
            out = new PrintWriter(new BufferedWriter(fileWriter, initialBufferSize));
            clipboardText = null;
        } else {
            return; // Cancel
        }

        final SwingWorker<Exception, Object> swingWorker = new SwingWorker<Exception, Object>() {

            @Override
            protected Exception doInBackground() throws Exception {
                Exception returnValue = null;
                ProgressMonitor pm = new DialogProgressMonitor(SnapApp.getDefault().getMainFrame(), DLG_TITLE,
                                                               Dialog.ModalityType.APPLICATION_MODAL);
                try {
                    final boolean mustCreateHeader = createHeaderBox.isSelected();
                    final boolean mustExportWavelengthsAndSF = exportWavelengthsAndSFBox.isSelected();
                    final boolean mustExportTiePoints = exportTiePointsBox.isSelected();
                    TransectExporter exporter = new TransectExporter(mustCreateHeader, mustExportWavelengthsAndSF, mustExportTiePoints);
                    boolean success = exporter.exportTransectPixels(out, raster.getProduct(),
                                                                    transectProfileData,
                                                                    numTransectPixels,
                                                                    pm);
                    if (success && clipboardText != null) {
                        SystemUtils.copyToClipboard(clipboardText.toString());
                        clipboardText.setLength(0);
                    }
                } catch (Exception e) {
                    returnValue = e;
                } finally {
                    out.close();
                }
                return returnValue;
            }

            @Override
            public void done() {
                // clear status bar
//                SnapApp.getDefault().clearStatusBarMessage();
                // show default-cursor
                UIUtils.setRootFrameDefaultCursor(SnapApp.getDefault().getMainFrame());
                // On error, show error message
                Exception exception;
                try {
                    exception = get();
                } catch (Exception e) {
                    exception = e;
                }
                if (exception != null) {
                    SnapDialogs.showError(DLG_TITLE,
                            ERR_MSG_BASE + exception.getMessage());
                }
            }
        };

        // show wait-cursor
        UIUtils.setRootFrameWaitCursor(SnapApp.getDefault().getMainFrame());
        // show message in status bar
        SnapApp.getDefault().setStatusBarMessage("Exporting transect pixels..."); /*I18N*/

        // Start separate worker thread.
        swingWorker.execute();
    }

    private static Shape convertToImageCoordinates(Shape shape, GeoCoding geoCoding) {
        AffineTransform m2iTransform;
        try {
            m2iTransform = ImageManager.getImageToModelTransform(geoCoding).createInverse();
        } catch (NoninvertibleTransformException ignored) {
            m2iTransform = new AffineTransform();
        }
        return m2iTransform.createTransformedShape(shape);
    }

    private static String createDefaultFileName(final RasterDataNode raster) {
        return FileUtils.getFilenameWithoutExtension(raster.getProduct().getName()) + "_TRANSECT.txt";
    }

    private static String getWindowTitle() {
        return SnapApp.getDefault().getAppName() + " - " + DLG_TITLE;
    }

    /**
     * Opens a modal file chooser dialog that prompts the user to select the output file name.
     * @return the selected file, <code>null</code> means "Cancel"
     */
    private static File promptForFile(String defaultFileName) {
        return    SnapDialogs.requestFileForSave(DLG_TITLE,
                false, null,
                ".txt",
                defaultFileName,
                null,
                "exportTransectPixels.lastDir");
    }

    private static int getNumTransectPixels(final Product product,
                                            final TransectProfileData transectProfileData) {

        final Point2D[] pixelPositions = transectProfileData.getPixelPositions();
        int numTransectPixels = 0;
        for (Point2D pixelPosition : pixelPositions) {
            int x = (int) Math.floor(pixelPosition.getX());
            int y = (int) Math.floor(pixelPosition.getY());
            if (x >= 0 && x < product.getSceneRasterWidth()
                && y >= 0 && y < product.getSceneRasterHeight()) {
                numTransectPixels++;
            }
        }
        return numTransectPixels;
    }


    @Override
    public HelpCtx getHelpCtx() {
        return new HelpCtx(HELP_ID);
    }

    @Override
    public Action createContextAwareInstance(Lookup lookup) {
        return null;
    }


    static class TransectExporter {

        private final boolean mustCreateHeader;
        private final boolean mustExportWavelengthsAndSF;
        private final boolean mustExportTiePoints;

        TransectExporter(boolean mustCreateHeader, boolean mustExportWavelengthsAndSF, boolean mustExportTiePoints) {
            this.mustCreateHeader = mustCreateHeader;
            this.mustExportWavelengthsAndSF = mustExportWavelengthsAndSF;
            this.mustExportTiePoints = mustExportTiePoints;
        }

        /**
         * Writes all pixel values of the given product within the given ROI to the specified out.
         *
         * @param out     the data output writer
         * @param product the product providing the pixel values
         *
         * @return <code>true</code> for success, <code>false</code> if export has been terminated (by user)
         */
        private boolean exportTransectPixels(final PrintWriter out,
                                             final Product product,
                                             final TransectProfileData transectProfileData,
                                             final int numTransectPixels,
                                             ProgressMonitor pm) {

            final Band[] bands = product.getBands();
            final TiePointGrid[] tiePointGrids = product.getTiePointGrids();
            final GeoCoding geoCoding = product.getGeoCoding();
            if (mustCreateHeader) {
                writeFileHeader(out, bands);
            }
            writeTableHeader(out, geoCoding, bands, mustExportTiePoints, tiePointGrids, mustExportWavelengthsAndSF);
            final Point2D[] pixelPositions = transectProfileData.getPixelPositions();

            pm.beginTask("Writing pixel data...", numTransectPixels);
            try {
                for (Point2D pixelPosition : pixelPositions) {
                    int x = (int) Math.floor(pixelPosition.getX());
                    int y = (int) Math.floor(pixelPosition.getY());
                    if (x >= 0 && x < product.getSceneRasterWidth()
                        && y >= 0 && y < product.getSceneRasterHeight()) {
                        writeDataLine(out, geoCoding, bands, mustExportTiePoints, tiePointGrids, x, y);
                        pm.worked(1);
                        if (pm.isCanceled()) {
                            return false;
                        }
                    }
                }
            } finally {
                pm.done();
            }

            return true;
        }

        private void writeFileHeader(PrintWriter out, Band[] bands) {

            ProductData.UTC utc = ProductData.UTC.create(new Date(), 0);
            out.printf("# Exported transect on %s%n", utc.format());
            if (bands.length >= 0) {
                Product product = bands[0].getProduct();
                out.printf("# Product name: %s%n", product.getName());
                if (product.getFileLocation() != null) {
                    out.printf("# Product file location: %s%n", product.getFileLocation().getAbsolutePath());
                }
            }
            out.println();

        }

        private void writeTableHeader(final PrintWriter out,
                                      final GeoCoding geoCoding,
                                      final Band[] bands,
                                      boolean mustExportTiePoints,
                                      TiePointGrid[] tiePointGrids,
                                      boolean mustExportWavelengthsAndSF) {
            if (mustExportWavelengthsAndSF) {
                float[] wavelengthArray = new float[bands.length];
                for (int i = 0; i < bands.length; i++) {
                    wavelengthArray[i] = bands[i].getSpectralWavelength();
                }
                out.printf("# Wavelength:\t \t \t \t%s\n", StringUtils.arrayToString(wavelengthArray, "\t"));

                float[] solarFluxArray = new float[bands.length];
                for (int i = 0; i < bands.length; i++) {
                    solarFluxArray[i] = bands[i].getSolarFlux();
                }
                out.printf("# Solar flux:\t \t \t \t%s%n", StringUtils.arrayToString(solarFluxArray, "\t"));
            }

            out.print("Pixel-X");
            out.print("\t");
            out.print("Pixel-Y");
            if (geoCoding != null) {
                out.print("\t");
                out.print("Longitude");
                out.print("\t");
                out.print("Latitude");
            }
            for (final Band band : bands) {
                out.print("\t");
                out.print(band.getName());
            }
            if (mustExportTiePoints) {
                for (final TiePointGrid grid : tiePointGrids) {
                    out.print("\t");
                    out.print(grid.getName());
                }
            }
            out.print("\n");
        }

        /**
         * Writes a data line of the dataset to be exported for the given pixel position.
         *
         * @param out                 the data output writer
         * @param geoCoding           the product's geo-coding
         * @param bands               the array of bands that provide pixel values
         * @param mustExportTiePoints if tie-points shall be exported
         * @param tiePointGrids       the array of tie-points that provide pixel values
         * @param x                   the current pixel's X coordinate
         * @param y                   the current pixel's Y coordinate
         */
        private void writeDataLine(final PrintWriter out,
                                   final GeoCoding geoCoding,
                                   final Band[] bands,
                                   boolean mustExportTiePoints,
                                   TiePointGrid[] tiePointGrids,
                                   int x, int y) {
            final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);

            out.print(String.valueOf(pixelPos.x));
            out.print("\t");
            out.print(String.valueOf(pixelPos.y));
            if (geoCoding != null) {
                out.print("\t");
                final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);
                out.print(String.valueOf(geoPos.lon));
                out.print("\t");
                out.print(String.valueOf(geoPos.lat));
            }
            for (final Band band : bands) {
                out.print("\t");
                final String pixelString = band.getPixelString(x, y);
                out.print(pixelString);
            }
            if (mustExportTiePoints) {
                for (final TiePointGrid grid : tiePointGrids) {
                    out.print("\t");
                    out.print(grid.getPixelString(x, y));
                }
            }

            out.print("\n");
        }
    }

}