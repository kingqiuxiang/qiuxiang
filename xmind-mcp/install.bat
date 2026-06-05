@echo off
setlocal
cd /d "%~dp0"

echo Installing xmind-mcp dependencies...
call npm install
if errorlevel 1 exit /b 1

echo Building xmind-mcp...
call npm run build
if errorlevel 1 exit /b 1

echo.
echo Done. Next steps:
echo 1. Edit .cursor\mcp.json and set your Windows paths
echo 2. Restart Cursor
echo 3. Ask Cursor to create a mind map in XMind
