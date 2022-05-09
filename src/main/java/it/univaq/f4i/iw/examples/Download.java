package it.univaq.f4i.iw.examples;

import it.univaq.f4i.iw.framework.result.StreamResult;
import it.univaq.f4i.iw.framework.security.SecurityHelpers;
import it.univaq.f4i.iw.framework.utils.ServletHelpers;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Giuseppe Della Penna
 */
public class Download extends HttpServlet {

    

    private void action_download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StreamResult result = new StreamResult(getServletContext());
        //estraiamo la resource ID dalla request (assumiamo che ci sia!)
        //extract the resource ID from the request object (we assume that there is one!)
        int res = (Integer) request.getAttribute("resource");
        //in base a res, dovremmo determinare la risorsa da scaricare, quindi aprire uno stream di input
        //per leggerla in binario
        //...
        //con la classe StreamResult possiamo usare un file o direttamente uno stream, anche aperto da una base di dati
        //qui, per esempio, prendiamo sempre un file di test all'interno della nostra applicazione
        //we should determine the file to download using the res parameter, but in this toy example we always download the same file
        //the StreamResult utility class provides methods to start a binary download from a file or any data stream
        //
        //usate questa versione per leggere una risorsa incorporata nel WAR se siete certi che sia stato espanso sul disco, o per una risorsa prelevata da una cartella esterna al contesto
        //use this version only if you want to read a resource embedded in the WAR file AND you know that it has been expanded to disk, or to read a resource from a folder outside the context
        File in = new File(getServletContext().getRealPath("") + File.separatorChar + "file_di_esempio.txt");
        result.setResource(in);
        result.activate(request, response);
        //
        //usate questa versione per leggere una risorsa incorporata nel WAR
        //use this version to read a resource is inside the WAR
//        URL url = getServletContext().getResource("/" + "file_di_esempio.txt");
//        if (url != null) {
//            result.setResource(url);
//            result.activate(request, response);
//        } else {
//            request.setAttribute("exception", new FileNotFoundException("Resource not found: "+url));
//            action_error(request, response);
//        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        try {
            int res = SecurityHelpers.checkNumeric(request.getParameter("res"));
            request.setAttribute("resource", res);
            action_download(request, response);
        } catch (NumberFormatException ex) {
            ServletHelpers.handleError("The requested resource is unavailable", request, response, getServletContext());
        } catch (IOException ex) {
            ServletHelpers.handleError(ex, request, response, getServletContext());
        }

    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Download me IW example - downloader";
    }// </editor-fold>
}
