import { app, BrowserWindow, dialog, ipcMain, Menu, shell, session } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { autoUpdater } from 'electron-updater';

// ---------------------------------------------------------------------------
// Конфигурация
// ---------------------------------------------------------------------------

/** Режим разработки. */
const IS_DEV = !app.isPackaged;

/** URL веб-клиента в режиме разработки. */
const DEV_SERVER_URL = process.env.IKTK_DEV_SERVER_URL || 'http://localhost:5173';

/** URL веб-клиента в продакшене (если используется удалённый сервер). */
const PROD_SERVER_URL = process.env.IKTK_SERVER_URL || '';

/** Каталог для локальных интеграций (обмен с CAD/CAM). */
const LOCAL_EXCHANGE_DIR =
  process.env.IKTK_LOCAL_EXCHANGE_DIR ||
  path.join(os.homedir(), '.iktk', 'incoming');

/** Каталог для временных файлов. */
const LOCAL_TEMP_DIR = path.join(os.tmpdir(), 'iktk-electron');

/** Каталог для логов. */
const LOG_DIR = path.join(app.getPath('userData'), 'logs');

// ---------------------------------------------------------------------------
// Состояние
// ---------------------------------------------------------------------------

let mainWindow: BrowserWindow | null = null;

// ---------------------------------------------------------------------------
// Подготовка окружения
// ---------------------------------------------------------------------------

/**
 * Создать рабочие каталоги.
 */
function ensureDirectories(): void {
  [LOCAL_EXCHANGE_DIR, LOCAL_TEMP_DIR, LOG_DIR].forEach((dir) => {
    try {
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
    } catch (e) {
      console.error(`Не удалось создать каталог ${dir}:`, e);
    }
  });
}

// ---------------------------------------------------------------------------
// Окно
// ---------------------------------------------------------------------------

/**
 * Создать главное окно приложения.
 *
 * <p>Особенности:
 * <ul>
 *   <li>сохранение размеров и позиции между запусками;</li>
 *   <li>безопасность: {@code nodeIntegration: false}, {@code contextIsolation: true};</li>
 *   <li>preload-скрипт для ограниченного IPC;</li>
 *   <li>внешние ссылки открываются в системном браузере;</li>
 *   <li>в режиме разработки подключаются DevTools.</li>
 * </ul>
 */
function createMainWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    title: 'ИКТК — Интегрированный конструкторско-технологический комплекс',
    backgroundColor: '#0d1117',
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
      webSecurity: true,
      spellcheck: false,
      devTools: IS_DEV,
    },
  });

  // Загрузка контента
  const url = IS_DEV ? DEV_SERVER_URL : PROD_SERVER_URL;
  if (url) {
    mainWindow.loadURL(url).catch((err) => {
      console.error('Ошибка загрузки URL:', err);
      showStartupError(err);
    });
  } else {
    // Production: загружаем локальный index.html из сборки
    const indexPath = path.join(__dirname, '..', 'web', 'dist', 'index.html');
    mainWindow.loadFile(indexPath).catch((err) => {
      console.error('Ошибка загрузки index.html:', err);
      showStartupError(err);
    });
  }

  // Показ окна после готовности
  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();

    if (IS_DEV) {
      mainWindow?.webContents.openDevTools({ mode: 'detach' });
    }
  });

  // Внешние ссылки — в системном браузере
  mainWindow.webContents.setWindowOpenHandler(({ url: target }) => {
    if (target.startsWith('http://') || target.startsWith('https://')) {
      shell.openExternal(target);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  // Навигация вне разрешённых доменов — блокируется
  mainWindow.webContents.on('will-navigate', (event, target) => {
    const allowed =
      target.startsWith(DEV_SERVER_URL) ||
      (PROD_SERVER_URL && target.startsWith(PROD_SERVER_URL)) ||
      target.startsWith('file://');
    if (!allowed) {
      event.preventDefault();
      shell.openExternal(target);
    }
  });

  // Обработка закрытия
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Ошибки рендеринга
  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    console.error('Render process gone:', details);
    dialog.showErrorBox(
      'Ошибка рендеринга',
      `Процесс рендеринга завершился с кодом ${details.exitCode}. Перезапустите приложение.`
    );
  });
}

/**
 * Показать окно с ошибкой запуска.
 */
function showStartupError(error: Error): void {
  dialog.showErrorBox(
    'Ошибка запуска ИКТК',
    `Не удалось загрузить веб-клиент.\n\n${error.message}\n\nПроверьте доступность сервера и повторите попытку.`
  );
  app.quit();
}

// ---------------------------------------------------------------------------
// Меню приложения
// ---------------------------------------------------------------------------

/**
 * Построить меню приложения.
 */
function buildMenu(): void {
  const template: Electron.MenuItemConstructorOptions[] = [
    {
      label: 'Файл',
      submenu: [
        {
          label: 'Открыть папку обмена',
          click: () => shell.openPath(LOCAL_EXCHANGE_DIR),
        },
        {
          label: 'Открыть папку логов',
          click: () => shell.openPath(LOG_DIR),
        },
        { type: 'separator' },
        {
          label: 'Обновить',
          click: () => mainWindow?.reload(),
        },
        { type: 'separator' },
        {
          label: 'Выход',
          accelerator: 'CmdOrCtrl+Q',
          click: () => app.quit(),
        },
      ],
    },
    {
      label: 'Правка',
      submenu: [
        { role: 'undo', label: 'Отменить' },
        { role: 'redo', label: 'Повторить' },
        { type: 'separator' },
        { role: 'cut', label: 'Вырезать' },
        { role: 'copy', label: 'Копировать' },
        { role: 'paste', label: 'Вставить' },
        { role: 'selectAll', label: 'Выделить всё' },
      ],
    },
    {
      label: 'Вид',
      submenu: [
        { role: 'reload', label: 'Перезагрузить' },
        { role: 'forceReload', label: 'Перезагрузить без кэша' },
        { type: 'separator' },
        { role: 'resetZoom', label: 'Обычный масштаб' },
        { role: 'zoomIn', label: 'Увеличить' },
        { role: 'zoomOut', label: 'Уменьшить' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: 'Полный экран' },
        ...(IS_DEV
          ? [{ role: 'toggleDevTools' as const, label: 'Инструменты разработчика' }]
          : []),
      ],
    },
    {
      label: 'Интеграции',
      submenu: [
        {
          label: 'Импорт CAD-модели…',
          click: () => handleImportCadFromMenu(),
        },
        {
          label: 'Загрузить файл в обмен…',
          click: () => handleUploadToExchangeFromMenu(),
        },
        { type: 'separator' },
        {
          label: 'Открыть каталог обмена',
          click: () => shell.openPath(LOCAL_EXCHANGE_DIR),
        },
      ],
    },
    {
      label: 'Справка',
      submenu: [
        {
          label: 'О программе',
          click: () => {
            dialog.showMessageBox({
              type: 'info',
              title: 'О программе',
              message: 'ИКТК — Интегрированный конструкторско-технологический комплекс',
              detail: `Версия: ${app.getVersion()}\nElectron: ${process.versions.electron}\nNode: ${process.versions.node}`,
              buttons: ['OK'],
            });
          },
        },
        {
          label: 'Проверить обновления',
          click: () => autoUpdater.checkForUpdatesAndNotify(),
        },
        { type: 'separator' },
        {
          label: 'Сообщить о проблеме',
          click: () =>
            shell.openExternal('https://example.com/iktk/support'),
        },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// ---------------------------------------------------------------------------
// IPC: обработчики
// ---------------------------------------------------------------------------

/**
 * Зарегистрировать IPC-обработчики.
 *
 * <p>Безопасность: рендерер не имеет прямого доступа к файловой системе,
 * все операции проходят через контролируемые каналы.
 */
function registerIpcHandlers(): void {
  // ---- Диалоги ---------------------------------------------------------

  /**
   * Открыть диалог выбора файла CAD.
   */
  ipcMain.handle('dialog:openCadFile', async () => {
    if (!mainWindow) return null;

    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Выберите CAD-модель',
      properties: ['openFile', 'multiSelections'],
      filters: [
        {
          name: 'CAD-модели',
          extensions: ['step', 'stp', 'iges', 'igs', 'jt', 'x_t', 'x_b', 'stl', '3dxml', 'm3d', 'a3d', 'cdw', 'frw', 'grb', 'par'],
        },
        { name: 'Все файлы', extensions: ['*'] },
      ],
    });

    if (result.canceled) return null;
    return result.filePaths;
  });

  /**
   * Открыть диалог выбора файла для загрузки в обмен.
   */
  ipcMain.handle('dialog:openAnyFile', async () => {
    if (!mainWindow) return null;

    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Выберите файл',
      properties: ['openFile', 'multiSelections'],
    });

    if (result.canceled) return null;
    return result.filePaths;
  });

  /**
   * Сохранить файл (диалог + запись).
   */
  ipcMain.handle(
    'dialog:saveFile',
    async (
      _event,
      payload: { defaultName?: string; content?: string; encoding?: 'utf8' | 'base64' }
    ) => {
      if (!mainWindow) return { success: false };

      const result = await dialog.showSaveDialog(mainWindow, {
        title: 'Сохранить файл',
        defaultPath: payload?.defaultName,
      });

      if (result.canceled || !result.filePath) {
        return { success: false };
      }

      try {
        const encoding = payload?.encoding === 'base64' ? 'base64' : 'utf8';
        fs.writeFileSync(result.filePath, payload?.content ?? '', { encoding });
        return { success: true, filePath: result.filePath };
      } catch (e) {
        return { success: false, error: (e as Error).message };
      }
    }
  );

  // ---- Каталог обмена --------------------------------------------------

  /**
   * Получить путь к каталогу обмена.
   */
  ipcMain.handle('exchange:getDir', () => LOCAL_EXCHANGE_DIR);

  /**
   * Сохранить файл в каталог обмена.
   *
   * <p>Используется для CLI-режима интеграции с CAD:
   * {@code FileExchangeService.resolveIncoming} (позиция 31) ищет
   * файлы в этом каталоге.
   */
  ipcMain.handle(
    'exchange:upload',
    async (_event, payload: { name: string; content: string; encoding?: 'utf8' | 'base64' }) => {
      try {
        const safeName = sanitizeFilename(payload?.name ?? `file-${Date.now()}`);
        const target = path.join(LOCAL_EXCHANGE_DIR, safeName);

        const encoding = payload?.encoding === 'base64' ? 'base64' : 'utf8';
        fs.writeFileSync(target, payload?.content ?? '', { encoding });

        return { success: true, filePath: target };
      } catch (e) {
        return { success: false, error: (e as Error).message };
      }
    }
  );

  /**
   * Список файлов в каталоге обмена.
   */
  ipcMain.handle('exchange:list', async () => {
    try {
      const entries = fs.readdirSync(LOCAL_EXCHANGE_DIR, { withFileTypes: true });
      return entries
        .filter((e) => e.isFile())
        .map((e) => {
          const fullPath = path.join(LOCAL_EXCHANGE_DIR, e.name);
          const stat = fs.statSync(fullPath);
          return {
            name: e.name,
            size: stat.size,
            modifiedAt: stat.mtime.toISOString(),
            path: fullPath,
          };
        });
    } catch (e) {
      console.error('exchange:list error:', e);
      return [];
    }
  });

  /**
   * Удалить файл из каталога обмена.
   */
  ipcMain.handle('exchange:delete', async (_event, fileName: string) => {
    try {
      const safeName = sanitizeFilename(fileName);
      const target = path.join(LOCAL_EXCHANGE_DIR, safeName);
      if (fs.existsSync(target)) {
        fs.unlinkSync(target);
        return { success: true };
      }
      return { success: false, error: 'Файл не найден' };
    } catch (e) {
      return { success: false, error: (e as Error).message };
    }
  });

  // ---- Файловая система (ограниченный доступ) -------------------------

  /**
   * Проверить существование файла.
   */
  ipcMain.handle('fs:exists', async (_event, filePath: string) => {
    try {
      return fs.existsSync(filePath);
    } catch {
      return false;
    }
  });

  /**
   * Получить размер файла.
   */
  ipcMain.handle('fs:size', async (_event, filePath: string) => {
    try {
      const stat = fs.statSync(filePath);
      return stat.size;
    } catch {
      return -1;
    }
  });

  /**
   * Прочитать файл.
   */
  ipcMain.handle(
    'fs:read',
    async (_event, payload: { filePath: string; encoding?: 'utf8' | 'base64' }) => {
      try {
        const encoding = payload?.encoding === 'base64' ? 'base64' : 'utf8';
        const content = fs.readFileSync(payload.filePath, { encoding });
        return { success: true, content };
      } catch (e) {
        return { success: false, error: (e as Error).message };
      }
    }
  );

  /**
   * Открыть файл во внешнем приложении.
   */
  ipcMain.handle('fs:openPath', async (_event, filePath: string) => {
    try {
      const result = await shell.openPath(filePath);
      return { success: result === '', error: result || undefined };
    } catch (e) {
      return { success: false, error: (e as Error).message };
    }
  });

  // ---- Приложение ------------------------------------------------------

  /**
   * Информация о приложении.
   */
  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    name: app.getName(),
    electron: process.versions.electron,
    node: process.versions.node,
    platform: process.platform,
    isDev: IS_DEV,
    userDataDir: app.getPath('userData'),
    exchangeDir: LOCAL_EXCHANGE_DIR,
    logsDir: LOG_DIR,
  }));

  /**
   * Открыть каталог обмена.
   */
  ipcMain.handle('app:openExchangeDir', () => shell.openPath(LOCAL_EXCHANGE_DIR));

  /**
   * Открыть каталог логов.
   */
  ipcMain.handle('app:openLogsDir', () => shell.openPath(LOG_DIR));

  /**
   * Проверить обновления.
   */
  ipcMain.handle('app:checkUpdates', async () => {
    try {
      const result = await autoUpdater.checkForUpdates();
      return { success: true, version: result?.updateInfo?.version };
    } catch (e) {
      return { success: false, error: (e as Error).message };
    }
  });
}

// ---------------------------------------------------------------------------
// Обработчики меню
// ---------------------------------------------------------------------------

/**
 * Импорт CAD-модели через меню.
 */
async function handleImportCadFromMenu(): Promise<void> {
  if (!mainWindow) return;

  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Выберите CAD-модель',
    properties: ['openFile', 'multiSelections'],
    filters: [
      {
        name: 'CAD-модели',
        extensions: ['step', 'stp', 'iges', 'igs', 'jt', 'x_t', 'x_b', 'stl', '3dxml', 'm3d', 'a3d', 'cdw'],
      },
      { name: 'Все файлы', extensions: ['*'] },
    ],
  });

  if (result.canceled || result.filePaths.length === 0) return;

  mainWindow.webContents.send('menu:import-cad', result.filePaths);
}

/**
 * Загрузка файла в каталог обмена через меню.
 */
async function handleUploadToExchangeFromMenu(): Promise<void> {
  if (!mainWindow) return;

  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Файл для загрузки в каталог обмена',
    properties: ['openFile', 'multiSelections'],
  });

  if (result.canceled || result.filePaths.length === 0) return;

  for (const src of result.filePaths) {
    try {
      const name = sanitizeFilename(path.basename(src));
      const dst = path.join(LOCAL_EXCHANGE_DIR, name);
      fs.copyFileSync(src, dst);
    } catch (e) {
      console.error(`Не удалось скопировать ${src}:`, e);
    }
  }

  mainWindow.webContents.send('menu:exchange-updated');
}

// ---------------------------------------------------------------------------
// Утилиты
// ---------------------------------------------------------------------------

/**
 * Санитизация имени файла — защита от path traversal.
 */
function sanitizeFilename(name: string): string {
  return name
    .replace(/[\\/]/g, '_')
    .replace(/\.\./g, '_')
    .replace(/[^a-zA-Z0-9_.\-]/g, '_');
}

// ---------------------------------------------------------------------------
// Безопасность: Content Security Policy
// ---------------------------------------------------------------------------

/**
 * Настройка CSP для сессии.
 */
function setupContentSecurityPolicy(): void {
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [
          "default-src 'self'; " +
            "connect-src 'self' http://localhost:* https:; " +
            "img-src 'self' data: blob:; " +
            "style-src 'self' 'unsafe-inline'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "font-src 'self' data:;",
        ],
      },
    });
  });
}

// ---------------------------------------------------------------------------
// Жизненный цикл приложения
// ---------------------------------------------------------------------------

// Ограничение на один экземпляр
const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
}

// Готовность приложения
app.whenReady().then(() => {
  ensureDirectories();
  setupContentSecurityPolicy();
  registerIpcHandlers();
  buildMenu();
  createMainWindow();

  // Проверка обновлений в продакшене
  if (!IS_DEV) {
    autoUpdater.checkForUpdatesAndNotify().catch((e) => {
      console.warn('Не удалось проверить обновления:', e);
    });
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createMainWindow();
    }
  });
});

// Закрытие всех окон (кроме macOS)
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// Обработка ошибок
process.on('uncaughtException', (error) => {
  console.error('Uncaught exception:', error);
  dialog.showErrorBox(
    'Непредвиденная ошибка',
    error.message || String(error)
  );
});

process.on('unhandledRejection', (reason) => {
  console.error('Unhandled rejection:', reason);
});

// ---------------------------------------------------------------------------
// Автообновление: события
// ---------------------------------------------------------------------------

autoUpdater.on('update-available', (info) => {
  mainWindow?.webContents.send('updater:available', info);
});

autoUpdater.on('update-downloaded', (info) => {
  mainWindow?.webContents.send('updater:downloaded', info);
  dialog
    .showMessageBox({
      type: 'info',
      title: 'Обновление готово',
      message: `Версия ${info.version} загружена. Перезапустить приложение сейчас?`,
      buttons: ['Перезапустить', 'Позже'],
      defaultId: 0,
      cancelId: 1,
    })
    .then((result) => {
      if (result.response === 0) {
        autoUpdater.quitAndInstall();
      }
    });
});

autoUpdater.on('error', (err) => {
  console.error('Ошибка автообновления:', err);
});