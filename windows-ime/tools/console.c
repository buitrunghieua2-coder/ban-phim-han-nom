// Minimal librime console: for each argument (a key sequence) print preedit + candidates.
// usage: rimec <user_data_dir> <schema_id> <keys>...   (keys use Rime key-sequence syntax, e.g. "nguoi" or "toi{space}")
#include <rime_api.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv) {
  if (argc < 4) { fprintf(stderr, "usage: %s user_dir schema keys...\n", argv[0]); return 2; }
  RimeApi *api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = "/usr/share/rime-data";
  traits.user_data_dir = argv[1];
  traits.distribution_name = "test";
  traits.distribution_code_name = "test";
  traits.distribution_version = "1";
  traits.app_name = "rime.test";
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  if (api->start_maintenance(True)) api->join_maintenance_thread();
  RimeSessionId s = api->create_session();
  if (!api->select_schema(s, argv[2])) { fprintf(stderr, "cannot select schema %s\n", argv[2]); return 1; }
  for (int i = 3; i < argc; i++) {
    api->clear_composition(s);
    api->simulate_key_sequence(s, argv[i]);
    RIME_STRUCT(RimeCommit, commit);
    if (api->get_commit(s, &commit)) { printf("%-14s commit: %s\n", argv[i], commit.text); api->free_commit(&commit); }
    RIME_STRUCT(RimeContext, ctx);
    if (api->get_context(s, &ctx)) {
      printf("%-14s [%s] ", argv[i], ctx.composition.preedit ? ctx.composition.preedit : "");
      for (int k = 0; k < ctx.menu.num_candidates && k < 9; k++) {
        printf("%d.%s", k + 1, ctx.menu.candidates[k].text);
        if (ctx.menu.candidates[k].comment) printf("(%s)", ctx.menu.candidates[k].comment);
        printf(" ");
      }
      printf("\n");
      api->free_context(&ctx);
    }
  }
  api->destroy_session(s);
  api->finalize();
  return 0;
}
