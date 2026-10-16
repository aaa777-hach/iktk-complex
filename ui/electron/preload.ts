import { contextBridge, ipcRenderer, IpcRendererEvent } from 'electron';

// ---------------------------------------------------------------------------
// Типы IPC-API
// ---------------------------------------------------------------------------

/**
 * Файл в каталоге обмена.
 */
export interface ExchangeFileInfo {
  name: string;
  size: number;
  modifiedAt: string;
  path: string;
}

/**
 * Результат операции с файлом.
 */
export interface FileOperationResult {
  success: boolean;
  filePath?: string;
  error?: string;
  content?: string;
}

/**
 * Информация о приложении.
 */
export interface AppInfo {
  version: string;
  name: string;
  electron: string;
  node: string;
  platform: NodeJS.Platform;
  isDev: boolean;
  userDataDir: string;
  exchangeDir: string;
  logsDir: string;
}

/**
 * Событие обновления.
 */
export interface UpdaterInfo {
  version: string;
  releaseDate?: string;
  releaseNotes?: string;
}

/**
 * Публичное API, доступное рендереру через {@code window.iktk}.
 */
export interface IktkBridge {
  /** Версия bridge-API (для совместимости). */
  readonly apiVersion: string;

  /** Признак запуска в Electron. */
  readonly isElectron: true;

  /** Информация о платформе. */
  readonly platform: NodeJS.Platform;

  // ---- Диалоги ---------------------------------------------------------
  dialog: {
    /** Выбрать CAD-модель (диалог с фильтром CAD-форматов). */
    openCadFile(): Promise<string[] | null>;
    /** Выбрать любой файл. */
    openAnyFile(): Promise<string[] | null>;
    /** Сохранить файл (диалог + запись). */
    saveFile(payload: {
      defaultName?: string;
      content?: string;
      encoding?: 'utf8' | 'base64';
    }): Promise<FileOperationResult>;
  };

  // ---- Каталог обмена --------------------------------------------------
  exchange: {
    /** Путь к каталогу обмена. */
    getDir(): Promise<string>;
    /** Загрузить файл в каталог обмена. */
    upload(payload: {
      name: string;
      content: string;
      encoding?: 'utf8' | 'base64';
    }): Promise<FileOperationResult>;
    /** Список файлов в каталоге обмена. */
    list(): Promise<ExchangeFileInfo[]>;
    /** Удалить файл из каталога обмена. */
    delete(fileName: string): Promise<FileOperationResult>;
  };

  // ---- Файловая система ------------------------------------------------
  fs: {
    /** Проверить существование файла. */
    exists(filePath: string): Promise<boolean>;
    /** Размер файла в байтах (-1 при ошибке). */
    size(filePath: string): Promise<number>;
    /** Прочитать файл. */
    read(payload: {
      filePath: string;
      encoding?: 'utf8' | 'base64';
    }): Promise<FileOperationResult>;
    /** Открыть файл во внешнем приложении. */
    openPath(filePath: string): Promise<FileOperationResult>;
  };

  // ---- Приложение ------------------------------------------------------
  app: {
    /** Информация о приложении. */
    info(): Promise<AppInfo>;
    /** Открыть каталог обмена. */
    openExchangeDir(): Promise<string>;
    /** Открыть каталог логов. */
    openLogsDir(): Promise<string>;
    /** Проверить обновления вручную. */
    checkUpdates(): Promise<{ success: boolean; version?: string; error?: string }>;
  };

  // ---- События ---------------------------------------------------------
  events: {
    /** Импорт CAD-модели через меню. */
    onImportCad(
      handler: (filePaths: string[]) => void
    ): () => void;
    /** Обновление каталога обмена через меню. */
    onExchangeUpdated(handler: () => void): () => void;
    /** Доступно обновление. */
    onUpdaterAvailable(
      handler: (info: UpdaterInfo) => void
    ): () => void;
    /** Обновление загружено. */
    onUpdaterDownloaded(
      handler: (info: UpdaterInfo) => void
    ): () => void;
  };
}

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Обёртка над {@code ipcRenderer.on} с отпиской.
 */
function onChannel<T>(
  channel: string,
  handler: (payload: T) => void
): () => void {
  const listener = (_event: IpcRendererEvent, payload: T) => handler(payload);
  ipcRenderer.on(channel, listener);
  return () => {
    ipcRenderer.removeListener(channel, listener);
  };
}

// ---------------------------------------------------------------------------
// Bridge API
// ---------------------------------------------------------------------------

/**
 * Публичное API Electron-оболочки ИКТК.
 *
 * <p>Предоставляется рендереру через {@link contextBridge} под именем
 * {@code window.iktk}. Прямой доступ к Node.js и Electron из рендерера
 * закрыт ({@code nodeIntegration: false}, {@code contextIsolation: true}).
 *
 * <p>Все методы соответствуют IPC-каналам, зарегистрированным в
 * {@code main.ts} (позиция 55). Возвращаемые значения сериализуемы
 * (structured clone).
 *
 * <p>Использование в веб-клиенте:
 * <pre>
 *   if (window.iktk?.isElectron) {
 *     const files = await window.iktk.dialog.openCadFile();
 *     await window.iktk.exchange.upload({ name: 'model.step', content: base64, encoding: 'base64' });
 *   } else {
 *     // fallback: обычная загрузка через браузер
 *   }
 * </pre>
 */
const iktkBridge: IktkBridge = {
  apiVersion: '1.0.0',

  isElectron: true,

  platform: process.platform,

  // ---- Диалоги ---------------------------------------------------------

  dialog: {
    openCadFile: () => ipcRenderer.invoke('dialog:openCadFile'),
    openAnyFile: () => ipcRenderer.invoke('dialog:openAnyFile'),
    saveFile: (payload) => ipcRenderer.invoke('dialog:saveFile', payload),
  },

  // ---- Каталог обмена --------------------------------------------------

  exchange: {
    getDir: () => ipcRenderer.invoke('exchange:getDir'),
    upload: (payload) => ipcRenderer.invoke('exchange:upload', payload),
    list: () => ipcRenderer.invoke('exchange:list'),
    delete: (fileName) => ipcRenderer.invoke('exchange:delete', fileName),
  },

  // ---- Файловая система ------------------------------------------------

  fs: {
    exists: (filePath) => ipcRenderer.invoke('fs:exists', filePath),
    size: (filePath) => ipcRenderer.invoke('fs:size', filePath),
    read: (payload) => ipcRenderer.invoke('fs:read', payload),
    openPath: (filePath) => ipcRenderer.invoke('fs:openPath', filePath),
  },

  // ---- Приложение ------------------------------------------------------

  app: {
    info: () => ipcRenderer.invoke('app:info'),
    openExchangeDir: () => ipcRenderer.invoke('app:openExchangeDir'),
    openLogsDir: () => ipcRenderer.invoke('app:openLogsDir'),
    checkUpdates: () => ipcRenderer.invoke('app:checkUpdates'),
  },

  // ---- События ---------------------------------------------------------

  events: {
    onImportCad: (handler) => onChannel<string[]>('menu:import-cad', handler),
    onExchangeUpdated: (handler) => onChannel<void>('menu:exchange-updated', handler),
    onUpdaterAvailable: (handler) => onChannel<UpdaterInfo>('updater:available', handler),
    onUpdaterDownloaded: (handler) => onChannel<UpdaterInfo>('updater:downloaded', handler),
  },
};

// ---------------------------------------------------------------------------
// Публикация в контекст окна
// ---------------------------------------------------------------------------

/**
 * Публикация bridge-API.
 *
 * <p>Если {@code contextIsolation} включён (по умолчанию в {@code main.ts},
 * позиция 55), используется {@link contextBridge.exposeInMainWorld}.
 * Рендерер получает доступ к API через {@code window.iktk}.
 */
try {
  contextBridge.exposeInMainWorld('iktk', iktkBridge);
} catch (e) {
  // Fallback: если contextIsolation отключён (не рекомендуется),
  // публикуем API напрямую в window.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).iktk = iktkBridge;
  // eslint-disable-next-line no-console
  console.warn(
    'preload: contextBridge недоступен, используется прямое присваивание window.iktk',
    e
  );
}

// ---------------------------------------------------------------------------
// Декларация глобального типа для рендерера
// ---------------------------------------------------------------------------

declare global {
  interface Window {
    /**
     * Bridge-API Electron-оболочки ИКТК.
     * Доступно только при запуске внутри Electron.
     */
    iktk?: IktkBridge;
  }
}

export {};