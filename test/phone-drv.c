/* Hilfsprogramm für test.sh: startet den ECHTEN Nolphin-Server (src/nolphin-phone-server.c) auf 127.0.0.1 und
 * schreibt eine Zeile "PORT TOKEN FINGERABDRUCK". Es läuft, bis die Standardeingabe schließt. */
#include <gio/gio.h>
#include <stdio.h>

#include "nolphin-phone-server.h"

static gpointer
watch_stdin (gpointer loop)
{
	gchar buf[64];

	while (fread (buf, 1, sizeof buf, stdin) > 0) {
	}
	g_main_loop_quit (loop);
	return NULL;
}

int
main (void)
{
	GError *error = NULL;
	gchar *root = g_dir_make_tmp ("nolphin-live-XXXXXX", NULL);
	gchar *inbox = g_build_filename (root, "eingang", NULL);
	gchar *share = g_build_filename (root, "frei", NULL);
	GFile *in = g_file_new_for_path (inbox), *sh = g_file_new_for_path (share);
	GMainLoop *loop = g_main_loop_new (NULL, FALSE);
	NolphinPhoneServer *s;
	gchar *token, *fp;

	g_mkdir_with_parents (share, 0755);
	s = nolphin_phone_server_start ("127.0.0.1", in, sh, NULL, NULL, NULL, NULL, &error);
	if (s == NULL) {
		g_printerr ("Start fehlgeschlagen: %s\n", error->message);
		return 1;
	}
	token = nolphin_phone_server_dup_token (s);
	fp = nolphin_phone_server_dup_fingerprint (s);
	g_print ("%u %s %s\n", nolphin_phone_server_get_port (s), token, fp);
	fflush (stdout);
	g_thread_new ("stdin", watch_stdin, loop);
	g_main_loop_run (loop);
	nolphin_phone_server_stop (s);
	return 0;
}
