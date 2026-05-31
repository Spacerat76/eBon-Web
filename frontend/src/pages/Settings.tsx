import React, { useEffect, useState } from 'react'
import { Input, Button, Card } from '../components/ui'
import { useSettings } from '../hooks/useSettings'
import type { SettingsPayload } from '../types/api'

export default function Settings() {
  const { data: settings, isLoading, updateMut, testPaperlessMut, testOpenRouterMut } = useSettings()
  const [form, setForm] = useState<SettingsPayload>({})
  const [paperlessTest, setPaperlessTest] = useState<string | null>(null)
  const [openRouterTest, setOpenRouterTest] = useState<string | null>(null)

  useEffect(() => {
    if (settings) {
      setForm({
        paperlessBaseUrl: settings.paperlessBaseUrl || '',
        openRouterModel: settings.openRouterModel || '',
        syncIntervalMinutes: settings.syncIntervalMinutes ?? undefined,
      })
    }
  }, [settings])

  const handleSave = async () => {
    setPaperlessTest(null)
    setOpenRouterTest(null)
    await updateMut.mutateAsync(form)
    alert('Settings saved')
  }

  const handleTestPaperless = async () => {
    setPaperlessTest('testing')
    try {
      const res = await testPaperlessMut.mutateAsync({ baseUrl: form.paperlessBaseUrl, apiToken: form.paperlessApiToken })
      setPaperlessTest(res.ok ? 'ok' : `error: ${res.message || 'unknown'}`)
    } catch (err: any) {
      setPaperlessTest(`error: ${err?.message || 'failed'}`)
    }
  }

  const handleTestOpenRouter = async () => {
    setOpenRouterTest('testing')
    try {
      const res = await testOpenRouterMut.mutateAsync({ apiKey: form.openRouterApiKey, model: form.openRouterModel })
      setOpenRouterTest(res.ok ? 'ok' : `error: ${res.message || 'unknown'}`)
    } catch (err: any) {
      setOpenRouterTest(`error: ${err?.message || 'failed'}`)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Settings</h2>
      </div>

      <Card>
        <div className="space-y-4">
          <div>
            <h3 className="text-sm font-medium mb-2">Paperless-NGX</h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <Input label="Base URL" value={form.paperlessBaseUrl || ''} onChange={(e) => setForm({ ...form, paperlessBaseUrl: e.target.value })} />
              <Input label="API Token" type="password" value={form.paperlessApiToken || ''} onChange={(e) => setForm({ ...form, paperlessApiToken: e.target.value })} />
              <div className="flex items-end gap-2">
                <Button variant="secondary" onClick={handleTestPaperless}>{testPaperlessMut.isLoading || paperlessTest === 'testing' ? 'Testing...' : 'Test Connection'}</Button>
                {paperlessTest && <div className="text-sm text-gray-600">{paperlessTest === 'ok' ? 'OK' : paperlessTest}</div>}
              </div>
            </div>
          </div>

          <div>
            <h3 className="text-sm font-medium mb-2">OpenRouter / AI</h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <Input label="API Key" type="password" value={form.openRouterApiKey || ''} onChange={(e) => setForm({ ...form, openRouterApiKey: e.target.value })} />
              <Input label="Model" value={form.openRouterModel || ''} onChange={(e) => setForm({ ...form, openRouterModel: e.target.value })} />
              <div className="flex items-end gap-2">
                <Button variant="secondary" onClick={handleTestOpenRouter}>{testOpenRouterMut.isLoading || openRouterTest === 'testing' ? 'Testing...' : 'Test Connection'}</Button>
                {openRouterTest && <div className="text-sm text-gray-600">{openRouterTest === 'ok' ? 'OK' : openRouterTest}</div>}
              </div>
            </div>
          </div>

          <div>
            <h3 className="text-sm font-medium mb-2">General</h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <Input label="Sync interval (minutes)" type="number" value={form.syncIntervalMinutes?.toString() || ''} onChange={(e) => setForm({ ...form, syncIntervalMinutes: e.target.value ? Number(e.target.value) : undefined })} />
              <div />
              <div className="flex items-end gap-2">
                <Button variant="primary" onClick={handleSave} disabled={updateMut.isLoading}>{updateMut.isLoading ? 'Saving...' : 'Save Settings'}</Button>
              </div>
            </div>
          </div>
        </div>
      </Card>
    </div>
  )
}
