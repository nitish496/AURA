package dev.lilt.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import dev.lilt.player.app.DebugHooks;
import dev.lilt.player.app.LibraryRepoImpl;
import dev.lilt.player.app.Shell;

/**
 * Thin host for the {@link Shell}: forwards lifecycle, permission, result and back events and
 * keeps the scanned library across configuration changes. All UI lives in dev.lilt.player.app.
 */
public final class MainActivity extends Activity {
    private Shell shell;
    private DebugHooks debug;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Object retained = getLastNonConfigurationInstance();
        shell = new Shell(this, state, retained instanceof LibraryRepoImpl.Model ? (LibraryRepoImpl.Model) retained : null);
        setContentView(shell.root());
        debug = new DebugHooks(this, shell);
        debug.handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        debug.handle(intent);
    }

    @Override public Object onRetainNonConfigurationInstance() { return shell == null ? null : shell.retain(); }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        shell.save(out);
    }

    /** Rotation / resize / fold: declared in the manifest's configChanges, so the shell re-lays out in place. */
    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (shell != null) shell.onConfigurationChanged(newConfig);
    }

    @Override protected void onStart() { super.onStart(); shell.onStart(); }
    @Override protected void onResume() { super.onResume(); shell.onResume(); }
    @Override protected void onPause() { shell.onPause(); super.onPause(); }

    @Override protected void onDestroy() {
        debug.cancel();
        shell.onDestroy();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        shell.onPermissionResult(request, permissions, results);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        shell.onActivityResult(request, result, data);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (!shell.handleBack()) super.onBackPressed();
    }
}
