const { join } = require('node:path');
const { createRequire } = require('node:module');

module.exports = function (config) {
  const projectRequire = createRequire(`${process.cwd()}/`);
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      'karma-jasmine',
      'karma-chrome-launcher',
      'karma-jasmine-html-reporter',
      'karma-coverage',
    ].map((plugin) => projectRequire(plugin)),
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: join(process.cwd(), 'coverage', 'angular-ui'),
      subdir: '.',
      includeAllSources: true,
      reporters: [{ type: 'html' }, { type: 'text-summary' }, { type: 'lcovonly' }],
      check: {
        global: {
          lines: 90,
        },
        each: {
          lines: 90,
        },
      },
    },
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-dev-shm-usage'],
      },
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    restartOnFileChange: true,
  });
};
