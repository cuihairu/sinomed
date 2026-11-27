'use strict';

// `@tarojs/cli` requires download-git-repo only in `taro init/create`'s remote
// template path; this repo uses `taro build` exclusively. The shim keeps that
// path explicit instead of silently no-op'ing: no vulnerable git-clone or
// decompress chain is installed.
module.exports = function downloadGitRepo() {
  throw new Error(
    'taro init/create remote templates are unavailable in this repo; the vulnerable download-git-repo chain was replaced with a no-op shim. Use a checked-in template instead.'
  );
};
