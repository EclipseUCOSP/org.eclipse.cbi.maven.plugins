package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Signs project main and attached artifact using <a
 * href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse jarsigner webservice</a>.
 * Only artifacts that have extension ``.jar'', other artifacts are not signed with a debug log message.
 * 
 * @goal sign
 * @phase package
 * @requiresProject
 * @description runs the eclipse signing process
 */
public class SignMojo
    extends AbstractMojo
{

    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * Official eclipse signer service url as described in
     * http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F
     */
    //private String signerUrl = "http://build.eclipse.org:31338/sign";
    private String signerUrl = "http://s.mjlim.net:1069/mikel/signing/winsign.php"; // Just for testing! This does no actual signing, just returns the file verbatim and logs at http://s.mjlim.net:1069/mikel/signing/sign_log.txt

    /**
     * @parameter expression="${project.build.directory}"
     */
    private File workdir;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

          // Try all of the files in the workdir
        if(workdir.isDirectory()){
            File[] files = workdir.listFiles();
            
            for(File file : files){
                signArtifact(file); // this will sign file if it is an exe, or return otherwise.
            }
        }

    }

    protected void signArtifact( File file )
        throws MojoExecutionException
    {
        try
        {
            if ( !file.isFile() || !file.canRead() )
            {
                return; // Can't read this. Likely a directory.
            }

            if ( !"exe".equals( getFileExtension(file) ) )
            {
                getLog().debug( "Artifact extention is not ``exe'', the artifact is not signed " + file );
                return;
            }

            if ( !shouldSign( file ) )
            {
                getLog().info( "Signing of " + file
                                   + " is disabled in META-INF/eclipse.inf, the artifact is not signed." );
                return;
            }

            final long start = System.currentTimeMillis();

            workdir.mkdirs();
            File tempSigned = File.createTempFile( file.getName(), ".signed-exe", workdir );
            try
            {
                signFile( file, tempSigned );
                if ( !tempSigned.canRead() || tempSigned.length() <= 0 )
                {
                    throw new MojoExecutionException( "Could not sign artifact " + file );
                }
                FileUtils.copyFile( tempSigned, file );
            }
            finally
            {
                tempSigned.delete();
            }
            getLog().info( "Signed " + file + " in " + ( ( System.currentTimeMillis() - start ) / 1000 )
                               + " seconds." );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not sign file " + file, e );
        }
    }

    private boolean shouldSign( File file )
        throws IOException
    {
        boolean sign = true;

        /*
         *
         * TODO: How can we determine whether an exe is acceptable to sign??
         *
         *
        JarFile jar = new JarFile( file );
        try
        {
            ZipEntry entry = jar.getEntry( "META-INF/eclipse.inf" );
            if ( entry != null )
            {
                InputStream is = jar.getInputStream( entry );
                Properties eclipseInf = new Properties();
                try
                {
                    eclipseInf.load( is );
                }
                finally
                {
                    is.close();
                }

                sign =
                    !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude" ) )
                        && !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude.sign" ) );
            }
        }
        finally
        {
            jar.close();
        }

        */

        return sign;
    }

    private void signFile( File source, File target )
        throws IOException, MojoExecutionException
    {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost( signerUrl );

        MultipartEntity reqEntity = new MultipartEntity();
        reqEntity.addPart( "file", new FileBody( source ) );
        post.setEntity( reqEntity );

        HttpResponse response = client.execute( post );
        int statusCode = response.getStatusLine().getStatusCode();

        HttpEntity resEntity = response.getEntity();
        if ( statusCode >= 200 && statusCode <= 299 && resEntity != null )
        {
            InputStream is = resEntity.getContent();
            try
            {
                FileUtils.copyStreamToFile( new RawInputStreamFacade( is ), target );
            }
            finally
            {
                IOUtil.close( is );
            }
        }
        else
        {
            throw new MojoExecutionException( "Signer replied " + response.getStatusLine() );
        }
    }

    private String getFileExtension(File f)
    {
        String name = f.getName();
        return name.substring(name.lastIndexOf('.')+1);
    }
}
